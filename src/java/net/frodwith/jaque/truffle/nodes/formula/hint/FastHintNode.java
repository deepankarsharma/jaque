package net.frodwith.jaque.truffle.nodes.formula.hint;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

import net.frodwith.jaque.Bail;
import net.frodwith.jaque.Location;
import net.frodwith.jaque.data.Atom;
import net.frodwith.jaque.data.Cell;
import net.frodwith.jaque.data.Noun;
import net.frodwith.jaque.truffle.Context;
import net.frodwith.jaque.truffle.FragmentationException;
import net.frodwith.jaque.truffle.TypesGen;
import net.frodwith.jaque.truffle.nodes.FragmentationNode;
import net.frodwith.jaque.truffle.nodes.formula.FormulaNode;

/* Fast hints are semantically only executed once, then rewritten
 * to a discard hint.
 */
public final class FastHintNode extends DynamicHintFormula {
  private final Context context;

  public FastHintNode(Context context, FormulaNode hint, FormulaNode next) {
    super(hint, next);
    this.context = context;
  }
  
  private Location register(Cell core, Clue clue) {
    if ( Atom.isZero(clue.parentAxis) ) {
      return new Location(clue.name, clue.name, 0L, clue.hooks, core, null);
    }
    else {
      CompilerAsserts.neverPartOfCompilation();
      FragmentationNode fragment = new FragmentationNode(clue.parentAxis);
      Cell parentCore;
      try {
        parentCore = TypesGen.expectCell(fragment.executeFragment(core));
      }
      catch ( FragmentationException e ) {
        throw new Bail();
      }
      catch ( UnexpectedResultException e ) {
        throw new Bail();
      }
      Cell parentBattery = TypesGen.asCell(parentCore.head);
      Location parentLoc = context.locations.get(parentBattery);
      if ( null == parentLoc ) {
        context.err("register: invalid parent for " + clue.name);
        return null;
      }
      String label = parentLoc.label + "/" + clue.name;
      return new Location(clue.name, label, clue.parentAxis, clue.hooks, core, parentLoc);
    }
  }

  public Object executeGeneric(VirtualFrame frame) {
    Object rawClue = hint.executeGeneric(frame);
    Object product = next.executeGeneric(frame);

    // We're on the slow path either way from here
    CompilerDirectives.transferToInterpreter();
    Clue clue = Clue.parse(rawClue, context);
    if ( TypesGen.isCell(product) && null != clue ) {
      Cell core = TypesGen.asCell(product);
      context.register(TypesGen.asCell(core.head), register(core, clue));
    }

    // possibly we could discard to next, but if hint is constant
    // truffle will optimize it away anyway and this more exactly
    // first the semantics if it is not
    FormulaNode discard = new DiscardHintNode(hint, next);
    replace(discard);
    return product;
  }
  
  private static final class ClueParsingException extends Exception {
  }
  
  private static final class Clue {
    public final String name;
    public final Object parentAxis;
    public final HashMap<String, Object> hooks;
    public static final Cell CONSTANT_ZERO = new Cell(1L, 0L);
    public static final Cell CONSTANT_FRAG = new Cell(0L, 1L);

    private Clue(String name, Object parentAxis, HashMap<String, Object> hooks) {
      this.name = name;
      this.parentAxis = parentAxis;
      this.hooks = hooks;
    }

    private static String chum(Object noun) throws ClueParsingException {
      if ( TypesGen.isCell(noun) ) {
        Cell c = TypesGen.asCell(noun);
        Object h = c.head,
               t = c.tail;

        if ( TypesGen.isCell(t) || !TypesGen.isLong(t) ) {
          throw new ClueParsingException();
        }

        long atom = TypesGen.asLong(t);
        int  small = (int) atom;
        if ( small != atom ) {
          throw new ClueParsingException();
        }

        String cord = Atom.cordToString(h);
        if ( null == cord ) {
          throw new ClueParsingException();
        }

        return String.format("%s%d", cord, small);
      }
      else {
        return Atom.cordToString(noun);
      }
    }
    
    private static Object skipHints(Object formula) {
      while ( true ) {
        if ( TypesGen.isCell(formula) ) {
          Cell c = TypesGen.asCell(formula);
          if ( Atom.equals(10L, c.head) ) {
            formula = TypesGen.asCell(c.tail).tail;
            continue;
          }
        }
        return formula;
      }
    }

    private static Object parseParentAxis(Object noun) throws ClueParsingException {
      Object o = skipHints(noun);
      if ( !TypesGen.isCell(o) ) {
        throw new ClueParsingException();
      }
      Cell f = TypesGen.asCell(o);
      if ( Cell.equals(CONSTANT_ZERO, f) ) {
        return 0L;
      }
      if ( !Noun.isAtom(f.tail) || !Atom.isZero(f.head) || Atom.cap(f.tail) != 3) {
        throw new ClueParsingException();
      }
      return f.tail;
    }
    
    private static Object parseHookAxis(Object nock) {
      nock = skipHints(nock);
      Cell f = TypesGen.asCell(nock);
      Object op = f.head;
      if ( Noun.isAtom(op) ) {
        if ( Atom.equals(0L, op) ) {
          if ( Noun.isAtom(f.tail) ) {
            return f.tail;
          }
        }
        else if ( Atom.equals(9L, op) ) {
          Cell rest = TypesGen.asCell(f.tail);
          if ( Noun.isAtom(rest.head) 
              && Cell.equals(CONSTANT_FRAG, TypesGen.asCell(rest.tail)) )
          {
            return rest.head;
          }
        }
      }
      return null;
    }

    private static HashMap<String,Object> parseHooks(Object noun) throws ClueParsingException {
      Object list = noun;
      HashMap<String, Object> map = new HashMap<String, Object>();
      // TODO: use data.List
      while ( !Atom.isZero(list) ) {
        Cell pair = TypesGen.asCell(list);
        Cell i = TypesGen.asCell(pair.head);
        Object t = i.head;
        if ( !Noun.isAtom(t) ) {
          throw new ClueParsingException();
        }
        String term = Atom.cordToString(i.head);
        if ( null == term ) {
          throw new ClueParsingException();
        }
        Cell nock = TypesGen.asCell(i.tail);
        Object axis = parseHookAxis(nock);
        if ( null != axis ) {
          map.put(term, axis);
        }
        list = pair.tail;
      }

      return map;
    }
    
    public static Clue parse(Object raw, Context context) {
      try {
        Cell trel = TypesGen.asCell(raw);
        Cell pair = TypesGen.asCell(trel.tail);
        String name = chum(trel.head);
        Object parentAxis = parseParentAxis(pair.head);
        HashMap<String, Object> hooks = parseHooks(pair.tail);
        return new Clue(name, parentAxis, hooks);
      }
      catch (ClassCastException e) {
        context.err("Bad noun shape while parsing clue");
        e.printStackTrace();
      }
      catch (ClueParsingException e) {
        context.err("Invalid clue");
        e.printStackTrace();
      }
      return null;
    }
  }
}
