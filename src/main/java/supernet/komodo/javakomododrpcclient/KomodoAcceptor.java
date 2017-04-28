/*
 * KomododRpcClient-JSON-RPC-Client License
 * 
 * Copyright (c) 2013, Mikhail Yevchenko.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the 
 * Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject
 * to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH
 * THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package supernet.komodo.javakomododrpcclient;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KomodoAcceptor implements Runnable {
    
    private static final Logger logger = Logger.getLogger(KomodoAcceptor.class.getCanonicalName());

    public final KomododRpcClient komodo;
    private String lastBlock, monitorBlock = null;
    int monitorDepth;
    private final LinkedHashSet<KomodoPaymentListener> listeners = new LinkedHashSet<KomodoPaymentListener>();

    public KomodoAcceptor(KomododRpcClient komodo, String lastBlock, int monitorDepth) {
        this.komodo = komodo;
        this.lastBlock = lastBlock;
        this.monitorDepth = monitorDepth;
    }
    
    public KomodoAcceptor(KomododRpcClient komodo) {
        this(komodo, null, 6);
    }

    public KomodoAcceptor(KomododRpcClient komodo, String lastBlock, int monitorDepth, KomodoPaymentListener listener) {
        this(komodo, lastBlock, monitorDepth);
        listeners.add(listener);
    }

    public KomodoAcceptor(KomododRpcClient komodo, KomodoPaymentListener listener) {
        this(komodo, null, 12);
        listeners.add(listener);
    }

    public String getAccountAddress(String account) throws KomodoRpcException {
        List<String> a = komodo.getAddressesByAccount(account);
        if (a.isEmpty())
            return komodo.getNewAddress(account);
        return a.get(0);
    }

    public synchronized String getLastBlock() {
        return lastBlock;
    }

    public synchronized void setLastBlock(String lastBlock) throws KomodoRpcException {
        if (this.lastBlock != null)
            throw new IllegalStateException("lastBlock already set");
        this.lastBlock = lastBlock;
        updateMonitorBlock();
    }

    public synchronized KomodoPaymentListener[] getListeners() {
        return listeners.toArray(new KomodoPaymentListener[0]);
    }

    public synchronized void addListener(KomodoPaymentListener listener) {
        listeners.add(listener);
    }

    public synchronized void removeListener(KomodoPaymentListener listener) {
        listeners.remove(listener);
    }

    private HashSet<String> seen = new HashSet<String>();

    private void updateMonitorBlock() throws KomodoRpcException {
        monitorBlock = lastBlock;
        for(int i = 0; i < monitorDepth && monitorBlock != null; i++) {
            KomododRpcClient.Block b = komodo.getBlock(monitorBlock);
            monitorBlock = b == null ? null : b.previousHash();
        }
    }

    public synchronized void checkPayments() throws KomodoRpcException {
        KomododRpcClient.TransactionsSinceBlock t = monitorBlock == null ? komodo.listSinceBlock() : komodo.listSinceBlock(monitorBlock);
        for (KomododRpcClient.Transaction transaction : t.transactions()) {
            if ("receive".equals(transaction.category())) {
                if (!seen.add(transaction.txId()))
                    continue;
                for (KomodoPaymentListener listener : listeners) {
                    try {
                        listener.transaction(transaction);
                    } catch (Exception ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                }
            }
        }
        if (!t.lastBlock().equals(lastBlock)) {
            seen.clear();
            lastBlock = t.lastBlock();
            updateMonitorBlock();
            for (KomodoPaymentListener listener : listeners) {
                try {
                    listener.block(lastBlock);
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    private boolean stop = false;
    
    public void stopAccepting() {
        stop = true;
    }
    
    private long checkInterval = 5000;

    /**
     * Get the value of checkInterval
     *
     * @return the value of checkInterval
     */
    public long getCheckInterval() {
        return checkInterval;
    }

    /**
     * Set the value of checkInterval
     *
     * @param checkInterval new value of checkInterval
     */
    public void setCheckInterval(long checkInterval) {
        this.checkInterval = checkInterval;
    }

    @Override
    public void run() {
        stop = false;
        long nextCheck = 0;
        while(!(Thread.interrupted() || stop)) {
            if (nextCheck <= System.currentTimeMillis())
                try {
                    nextCheck = System.currentTimeMillis() + checkInterval;
                    checkPayments();
                } catch (KomodoRpcException ex) {
                    Logger.getLogger(KomodoAcceptor.class.getName()).log(Level.SEVERE, null, ex);
                }
            else
                try {
                    Thread.sleep(Math.max(nextCheck - System.currentTimeMillis(), 100));
                } catch (InterruptedException ex) {
                    Logger.getLogger(KomodoAcceptor.class.getName()).log(Level.WARNING, null, ex);
                }
        }
    }

//    public static void main(String[] args) {
//        //System.out.println(System.getProperties().toString().replace(", ", ",\n"));
//        final KomododRpcClient komodo = new KomodoJSONRPCClient(true);
//        new KomodoAcceptor(komodo, null, 6, new KomodoPaymentListener() {
//
//            public void block(String blockHash) {
//                try {
//                    System.out.println("new block: " + blockHash + "; date: " + komodo.getBlock(blockHash).time());
//                } catch (KomodoRpcException ex) {
//                    logger.log(Level.SEVERE, null, ex);
//                }
//            }
//
//            public void transaction(Transaction transaction) {
//                System.out.println("tx: " + transaction.confirmations() + "\t" + transaction.amount() + "\t=> " + transaction.account());
//            }
//        }).run();
//    }

}
