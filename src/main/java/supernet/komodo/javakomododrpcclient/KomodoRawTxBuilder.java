/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package supernet.komodo.javakomododrpcclient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author azazar
 */
public class KomodoRawTxBuilder {

  public final KomododRpcClient komodo;

  public KomodoRawTxBuilder(KomododRpcClient komodo) {
    this.komodo = komodo;
  }
  public Set<KomododRpcClient.TxInput> inputs = new LinkedHashSet<>();
  public List<KomododRpcClient.TxOutput> outputs = new ArrayList<>();

  private class Input extends KomododRpcClient.BasicTxInput {

    public Input(String txid, int vout) {
      super(txid, vout);
    }

    public Input(KomododRpcClient.TxInput copy) {
      this(copy.txid(), copy.vout());
    }

    @Override
    public int hashCode() {
      return txid.hashCode() + vout;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null)
        return false;
      if (!(obj instanceof KomododRpcClient.TxInput))
        return false;
      KomododRpcClient.TxInput other = (KomododRpcClient.TxInput) obj;
      return vout == other.vout() && txid.equals(other.txid());
    }

  }

  public KomodoRawTxBuilder in(KomododRpcClient.TxInput in) {
    inputs.add(new Input(in.txid(), in.vout()));
    return this;
  }

  public KomodoRawTxBuilder in(String txid, int vout) {
    in(new KomododRpcClient.BasicTxInput(txid, vout));
    return this;
  }

  public KomodoRawTxBuilder out(String address, double amount) {
    if (amount <= 0d)
      return this;
    outputs.add(new KomododRpcClient.BasicTxOutput(address, amount));
    return this;
  }

  public KomodoRawTxBuilder in(double value) throws KomodoRpcException {
    return in(value, 6);
  }

  public KomodoRawTxBuilder in(double value, int minConf) throws KomodoRpcException {
    List<KomododRpcClient.Unspent> unspent = komodo.listUnspent(minConf);
    double v = value;
    for (KomododRpcClient.Unspent o : unspent) {
      if (!inputs.contains(new Input(o))) {
        in(o);
        v = KomodoUtil.normalizeAmount(v - o.amount());
      }
      if (v < 0)
        break;
    }
    if (v > 0)
      throw new KomodoRpcException("Not enough komodos (" + v + "/" + value + ")");
    return this;
  }

  private HashMap<String, KomododRpcClient.RawTransaction> txCache = new HashMap<>();

  private KomododRpcClient.RawTransaction tx(String txId) throws KomodoRpcException {
    KomododRpcClient.RawTransaction tx = txCache.get(txId);
    if (tx != null)
      return tx;
    tx = komodo.getRawTransaction(txId);
    txCache.put(txId, tx);
    return tx;
  }

  public KomodoRawTxBuilder outChange(String address) throws KomodoRpcException {  //proper exceptionhandling
    return outChange(address, 0d);
  }

  public KomodoRawTxBuilder outChange(String address, double fee) throws KomodoRpcException {
    double is = 0d;
    for (KomododRpcClient.TxInput i : inputs)
      is = KomodoUtil.normalizeAmount(is + tx(i.txid()).vOut().get(i.vout()).value());
    double os = fee;
    for (KomododRpcClient.TxOutput o : outputs)
      os = KomodoUtil.normalizeAmount(os + o.amount());
    if (os < is)
      out(address, KomodoUtil.normalizeAmount(is - os));
    return this;
  }

  public String create() throws KomodoRpcException {
    return komodo.createRawTransaction(new ArrayList<>(inputs), outputs);
  }

  public String sign() throws KomodoRpcException {
    return komodo.signRawTransaction(create(), null, null);
  }

  public String send() throws KomodoRpcException {
    return komodo.sendRawTransaction(sign());
  }

}
