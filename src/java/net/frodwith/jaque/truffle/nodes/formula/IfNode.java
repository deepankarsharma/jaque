package net.frodwith.jaque.truffle.nodes.formula;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

import net.frodwith.jaque.Bail;

public final class IfNode extends FormulaNode {
  @Child private FormulaNode test;
  @Child private FormulaNode yes;
  @Child private FormulaNode no;
  private final ConditionProfile condition; 
  private final BranchProfile nonLoobeanTest = BranchProfile.create();
  
  public IfNode(FormulaNode test, FormulaNode yes, FormulaNode no) {
    this.test = test;
    this.yes = yes;
    this.no = no;
    this.condition = ConditionProfile.createCountingProfile();
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    long answer;
    try {
      answer = test.executeLong(frame);
      if ( answer == 0L || answer == 1L ) {
        if ( condition.profile(0L == answer) ) {
          return yes.executeGeneric(frame);
        }
        else {
          return no.executeGeneric(frame);
        }
      }
    }
    catch (UnexpectedResultException e) {
    }
    nonLoobeanTest.enter();
    throw new Bail();
  }
}
