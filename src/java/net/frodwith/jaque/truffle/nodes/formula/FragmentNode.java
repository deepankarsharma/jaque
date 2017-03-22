package net.frodwith.jaque.truffle.nodes.formula;

import com.oracle.truffle.api.frame.VirtualFrame;

import net.frodwith.jaque.truffle.nodes.FragmentationNode;

public class FragmentNode extends FormulaNode {
  @Child private FragmentationNode f;
  
  public FragmentNode(Object axis) {
    this.f = new FragmentationNode(axis);
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return f.executeFragment(getSubject(frame));
  }

}