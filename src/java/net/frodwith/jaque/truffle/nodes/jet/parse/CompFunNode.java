package net.frodwith.jaque.truffle.nodes.jet.parse;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import net.frodwith.jaque.data.Cell;
import net.frodwith.jaque.data.Parse;
import net.frodwith.jaque.truffle.Context;
import net.frodwith.jaque.truffle.nodes.jet.ImplementationNode;

/* XX: disabled for now, as it offers no performance benefit on its own.
 *     jetting the entire parsing suite (as vere does) might offer some improvement.
 */

public abstract class CompFunNode extends ImplementationNode {
 protected abstract Object executeCompFun(VirtualFrame frame, Cell raq, Cell vax, Cell sab);
 
 public Object doJet(VirtualFrame frame, Object subject) {
   Cell cor = Cell.expect(subject),
        pay = Cell.expect(cor.tail),
        sam = Cell.expect(pay.head),
        cag = Cell.expect(pay.tail),
        cap = Cell.expect(cag.tail),
        raq = Cell.expect(cap.head),
        vax = Cell.expect(sam.head),
        sab = Cell.expect(sam.tail);

   return executeCompFun(frame, raq, vax, sab);
 }

  @Specialization
  protected Object compFun(Cell raq, Cell vax, Cell sab) {
    Context c = getContext();
    return Parse.compFun(c.slammer(raq), vax, c.slammer(sab));
  }

}