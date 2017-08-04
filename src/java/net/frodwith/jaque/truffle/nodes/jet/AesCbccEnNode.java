package net.frodwith.jaque.truffle.nodes.jet;

import com.oracle.truffle.api.dsl.Specialization;

import net.frodwith.jaque.data.Atom;

public abstract class AesCbccEnNode extends TrelGateNode {

  @Specialization
  protected Object en(Object key, Object iv, Object msg) {
    return Atom.aes_cbcc_en(key, iv, msg);
  }

}