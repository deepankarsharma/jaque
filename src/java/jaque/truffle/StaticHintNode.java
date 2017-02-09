package jaque.truffle;

import jaque.interpreter.Result;
import jaque.noun.*;

import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeInfo(shortName = "static-hint")
public final class StaticHintNode extends HintNode {
  @Child private Formula f;

  public StaticHintNode(Atom kind, Formula f) {
    super(kind);
    this.f = f;
  }

  public Atom clue(VirtualFrame frame) {
    return Atom.ZERO;
  }

  public Cell rawNext() {
    return f.toNoun();
  }
  
  public Formula next() {
    return f;
  }

  public Cell toNoun() {
    return new Cell(Atom.fromLong(10), new Cell(this.kind, f.toNoun()));
  }
}
