package net.frodwith.jaque.truffle.bloc;

import java.util.Deque;

import com.oracle.truffle.api.frame.VirtualFrame;

public final class SameNode extends OpNode {
  @Child private SameOpNode same;
  
  public SameNode() {
    this.same = SameOpNodeGen.create();
    insert(same);
  }

  @Override
  public void execute(VirtualFrame frame) {
    Deque<Object> s = getStack(frame);
    Object a = s.pop();
    Object b = s.pop();
    s.push(same.executeSame(frame, a, b));
  }

}
