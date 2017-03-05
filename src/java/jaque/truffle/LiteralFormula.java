package jaque.truffle;

import com.oracle.truffle.api.frame.VirtualFrame;

import jaque.noun.Cell;

public abstract class LiteralFormula extends SafeFormula {
  public abstract Object getValue();
  
  @Override
  public Cell toCell() {
    return new Cell(1L, getValue());
  }
}
