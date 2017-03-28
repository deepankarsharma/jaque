package net.frodwith.jaque.data;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import com.oracle.truffle.api.nodes.UnexpectedResultException;

import gnu.math.MPN;
import net.frodwith.jaque.Bail;
import net.frodwith.jaque.truffle.TypesGen;

/* Atoms are primitive (unboxed) longs unless they don't fit
 * in which case they're represented as int[] (NOT java bigint,
 * because it doesn't give us fine-grained enough access for hoon
 * functions like cut).
 * 
 * Please implement all library functions involving atoms (cut, etc)
 * as static methods accepting and returning Object in this class.
 * 
 * Methods in this class assume that their argument is some type of
 * atom (either long or array of ints in little-endian byte-order).
 * Passing any of these functions other types of objects(ints, etc)
 * has undefined behavior.
 * 
 * Many of these methods were adapted from urbit's vere code. The ones
 * referencing MPN were also developed with reference to Kawa Scheme's
 * IntNum class.
 */

public class Atom {
  // get two equally sized int[]s for mpn functions
  private static class Square {
    int[] x;
    int[] y;
    int   len;

    public Square(Object a, Object b) {
      int[] aw = TypesGen.asImplicitIntArray(a), bw = TypesGen.asImplicitIntArray(b);
      int   as = aw.length, bs = bw.length;
      if (as > bs) {
        len = as;
        x   = aw;
        y   = new int[len];
        System.arraycopy(bw, 0, y, 0, bs);
      }
      else if (as < bs) {
        len = bs;
        x   = new int[len];
        y   = bw;
        System.arraycopy(aw, 0, x, 0, as);
      }
      else {
        len = as;
        x   = aw;
        y   = bw;
      }
    }
  }
  private static final int[] MINIMUM_INDIRECT = new int[] {0, 0, 1};
  public static final boolean BIG_ENDIAN = true;
  public static final boolean LITTLE_ENDIAN = false;
  public static final long YES = 0L;
  public static final long NO = 1L;
  public static final Object FAST = stringToCord("fast");
  
  public static final Object MEMO = stringToCord("memo");
  
  
  public static int[] add(int[] a, int[] b) {
    Square s   = new Square(a, b);
    int[] dst  = new int[s.len+1];
    dst[s.len] = MPN.add_n(dst, s.x, s.y, s.len);
    return dst;
  }
  
  public static long add(long a, long b) throws ArithmeticException {
    return Math.addExact(a, b);
  }
  
  public static Object add(Object a, Object b) {
    if ( TypesGen.isLong(a) && TypesGen.isLong(b) ) {
      try {
        return add(TypesGen.asLong(a), TypesGen.asLong(b));
      }
      catch (ArithmeticException e) {
      }
    }
    return add(TypesGen.asImplicitIntArray(a), TypesGen.asImplicitIntArray(b));
  }
  
  public static Object bex(long a) {
    if (a < 64) {
      return 1L << a;
    }
    else {
      int whole = (int) (a >> 5),
          parts = (int) a & 0xFFFF,
          len = parts > 0 ? whole + 2 : whole + 1;

      int[] words = new int[len];
      words[whole] = 1 << parts;
      return words;
    }
  }
  
  public static byte bloq(long atom) {
    if ( atom >= 32 || atom < 0) {
      throw new Bail();
    }
    return (byte) atom;
  }
  
  public static Object can(byte a, Iterable<Object> b) {
    int tot = 0;

    try {
      for ( Object i : b ) {
        Cell c = TypesGen.expectCell(i);
        long pil = TypesGen.expectLong(c.head);
        int pi = (int) pil;
        
        if (pi != pil) {
          throw new Bail();
        }

        Object qi = c.tail;
        if ( !Noun.isAtom(qi) ) {
          throw new Bail();
        }
        tot = Math.addExact(tot, pi);
      }
    }
    catch (ArithmeticException e) {
      throw new Bail();
    }
    catch (UnexpectedResultException e) {
      throw new Bail();
    }

    if ( 0 == tot ) {
      return 0L;
    }

    int[] sal = slaq(a, tot);
    int pos = 0;
    
    for ( Object i : b ) {
      Cell c = TypesGen.asCell(i);
      int pi = (int) TypesGen.asLong(c.head);
      chop(a, 0, pi, pos, sal, c.tail);
      pos += pi;
    }

    return malt(sal);
  }

  public static int cap(Object atom) {
    int b = met(atom);
    if ( b < 2 ) {
      throw new Bail();
    }
    return getNthBit(atom, b - 2) ? 3 : 2;
  }
  
  public static void chop(byte met, int fum, int wid, int tou, int[] dst, Object src) {
    int[] buf = TypesGen.asImplicitIntArray(src);
    int   len = buf.length, i;

    if (met < 5) {
      int san = 1 << met,
      mek = ((1 << san) - 1),
      baf = fum << met,
      bat = tou << met;

      for (i = 0; i < wid; ++i) {
        int waf = baf >>> 5,
            raf = baf & 31,
            wat = bat >>> 5,
            rat = bat & 31,
            hop;

        hop = (waf >= len) ? 0 : buf[waf];
        hop = (hop >>> raf) & mek;
        dst[wat] ^= hop << rat;
        baf += san;
        bat += san;
      }
    }
    else {
      int hut = met - 5,
          san = 1 << hut,
          j;

      for (i = 0; i < wid; ++i) {
        int wuf = (fum + i) << hut,
            wut = (tou + i) << hut;

        for (j = 0; j < san; ++j) {
          dst[wut + j] ^= ((wuf + j) >= len)
                       ? 0
                       : buf[wuf + j];
        }
      }
    }
  }
  
  public static int compare(int[] a, int[] b) {
    return MPN.cmp(a, a.length, b, b.length);
  } 
  
  public static int compare(long a, long b) {
    if (a == b) {
      return 0;
    }
    else {
      boolean lth = a < b;
      if ( (a < 0) != (b < 0) ) {
        lth = !lth;
      }
      return lth ? -1 : 1;
    }
  }
  
  // -1, 0, 1 for less than, equal, or greater than respectively
  public static int compare(Object a, Object b) {
    if ( TypesGen.isLong(a) && TypesGen.isLong(b) ) {
      return compare(TypesGen.asLong(a), TypesGen.asLong(b));
    } 
    return compare(TypesGen.asImplicitIntArray(a), TypesGen.asImplicitIntArray(b));
  }
  
  public static long con(long a, long b) {
    return a | b;
  }

  public static Object con(Object a, Object b) {
    byte w   = 5;
    int  lna = met(w, a);
    int  lnb = met(w, b);

    if ( (0 == lna) && (0 == lnb) ) {
      return 0L;
    }
    else {
      int i, len = Math.max(lna, lnb);
      int[] sal  = new int[len];
      int[] bow  = TypesGen.asImplicitIntArray(b);

      chop(w, 0, lna, 0, sal, a);

      for ( i = 0; i < lnb; i++ ) {
        sal[i] |= bow[i];
      }

      return malt(sal);
    } 
  }
  
  public static long dis(long a, long b) {
    return a & b;
  }

  public static Object dis(Object a, Object b) {
    byte w   = 5;
    int  lna = met(w, a);
    int  lnb = met(w, b);

    if ( (0 == lna) && (0 == lnb) ) {
      return 0L;
    }
    else {
      int i, len = Math.max(lna, lnb);
      int[] sal  = new int[len];
      int[] bow  = TypesGen.asImplicitIntArray(b);

      chop(w, 0, lna, 0, sal, a);

      for ( i = 0; i < len; i++ ) {
        sal[i] &= (i >= lnb) ? 0 : bow[i];
      }

      return malt(sal);
    } 
  }
  
  public static String cordToString(Object atom) {
    try {
      return new String(toByteArray(atom, LITTLE_ENDIAN), "UTF-8");
    }
    catch (UnsupportedEncodingException e) {
      return null;
    }
  }
  
  public static Object dec(int[] atom) {
    int[] result;
    if ( atom[0] == 0 ) {
      result = new int[atom.length - 1];
      Arrays.fill(result, 0xFFFFFFFF);
    }
    else {
      result = Arrays.copyOf(atom, atom.length);
      result[0] -= 1;
    }
    return malt(result);
  }

  public static long dec(long atom) {
    if ( atom == 0 ) {
      throw new Bail();
    }
    else {
      return atom - 1;
    }
  }
  
  public static Object dec(Object atom) {
    if ( TypesGen.isLong(atom) ) {
      return dec(TypesGen.asLong(atom));
    }
    else {
      return dec(TypesGen.asIntArray(atom));
    }
  }
  
  public static Object div(int[] x, int[] y) {
    int cmp = compare(x,y);
    if ( cmp < 0 ) {
      return 0L;
    }
    else if ( 0 == cmp ) {
      return 1L;
    }
    else if ( 1 == y.length ) {
      int[] q = new int[x.length];
      MPN.divmod_1(q, x, x.length, y[0]);
      return malt(q);
    }
    else {
      return malt(
          Arrays.copyOfRange(divmod(x,y), y.length, x.length + 3 - y.length));
    }
  }
  
  public static long div(long a, long b) {
    return a / b;
  }
  
  public static Object div(Object a, Object b) {
    if ( TypesGen.isLong(a) && TypesGen.isLong(b) ) {
      return div(TypesGen.asLong(a), TypesGen.asLong(b));
    }
    return div(TypesGen.asImplicitIntArray(a), TypesGen.asImplicitIntArray(b));
  }

  private static int[] divmod(int[] x, int[] y) {
    int xlen = x.length,
        ylen = y.length;
    int[] xwords = Arrays.copyOf(x, xlen+2),
          ywords = Arrays.copyOf(y, ylen);

    int nshift = MPN.count_leading_zeros(ywords[ylen-1]);
    if (nshift != 0) {
      MPN.lshift(ywords, 0, ywords, ylen, nshift);
      int x_high = MPN.lshift(xwords, 0, xwords, xlen, nshift);
      xwords[xlen++] = x_high;
    }

    if (xlen == ylen) {
      xwords[xlen++] = 0;
    }

    MPN.divide(xwords, xlen, ywords, ylen);
    return xwords;
  }
  
  public static boolean equals(int[] a, int[] b) {
    return Arrays.equals(a,  b);
  }
  
  public static boolean equals(long a, long b) {
    return a == b;
  }
  
  public static boolean equals(Object a, Object b) {
    return ( TypesGen.isLong(a) 
        && TypesGen.isLong(b)
        && equals(TypesGen.asLong(a), TypesGen.asLong(b)) )
      || ( TypesGen.isIntArray(a)
        && TypesGen.isIntArray(b)
        && equals(TypesGen.asIntArray(a), TypesGen.asIntArray(b)));
  }
  
  public static Object expect(Object o) {
    if ( !Noun.isAtom(o) ) {
      throw new Bail();
    }
    return o;
  }
  
  public static Object fromByteArray(byte[] pill, boolean endian) {
    int len  = pill.length;
    int trim = len % 4;

    if (endian == BIG_ENDIAN) {
      pill = Arrays.copyOf(pill, len);
      reverseBytes(pill);
    }

    if (trim > 0) {
      int    nlen = len + (4-trim);
      byte[] npil = new byte[nlen];
      System.arraycopy(pill, 0, npil, 0, len);
      pill = npil;
      len = nlen;
    }

    int   size  = len / 4;
    int[] words = new int[size];
    int i, b, w;
    for (i = 0, b = 0; i < size; ++i) {
      w =  (pill[b++] << 0)  & 0x000000FF;
      w ^= (pill[b++] << 8)  & 0x0000FF00;
      w ^= (pill[b++] << 16) & 0x00FF0000;
      w ^= (pill[b++] << 24) & 0xFF000000;
      words[i] = w;
    }

    return malt(words);
  }
  
  public static Object fromString(String s) {
      return fromString(s, 10);
  }
  
  public static Object fromString(String s, int radix) {
    char[] car = s.toCharArray();
    int    len = car.length,
           cpw = MPN.chars_per_word(radix),
           i;
    byte[] dig = new byte[len];
    int[]  wor = new int[(len / cpw) + 1];

    for (i = 0; i < len; ++i) {
        dig[i] = (byte) Character.digit(car[i], radix);
    }

    MPN.set_str(wor, dig, len, radix);

    return malt(wor);
}
  
  public static boolean getNthBit(int[] atom, int n) {
    int pix = n >> 5;
    
    if ( pix >= atom.length ) {
      return false;
    }
    else {
      return (1 & (atom[pix] >>> (n & 31))) != 0;
    }
  }
  
  public static boolean getNthBit(long atom, int n) {
    if ( n >= (Long.SIZE - 1) ) {
      return false;
    }
    else {
      return ((atom & (1L << n)) != 0);
    }
  }
  
  public static boolean getNthBit(Object atom, int n) {
    if ( atom instanceof Long ) {
      return getNthBit((long) atom, n);
    }
    else {
      return getNthBit((int[]) atom, n);
    }
  }
  
  public static int[] increment(int[] atom) {
    int top = atom[atom.length];
    try {
      int newTop = Math.incrementExact(top);
      int[] dst = new int[atom.length];
      System.arraycopy(atom, 0, dst, 0, atom.length - 1);
      dst[atom.length] = newTop;
      return dst;
    } 
    catch (ArithmeticException e) {
      int[] w = new int[atom.length + 1];
      w[atom.length] = 1;
      return w;
    }
  }
  
  public static long increment(long atom) throws ArithmeticException {
    return Math.incrementExact(atom);
  }
  
  public static Object increment(Object atom) {
    if ( TypesGen.isLong(atom) ) {
      try {
        return increment(TypesGen.asLong(atom));
      } 
      catch (ArithmeticException e) {
        return MINIMUM_INDIRECT;
      }
    }
    else {
      return increment(TypesGen.asIntArray(atom));
    }
  }

  public static boolean isLessThanUnsigned(long n1, long n2) {
    boolean comp = (n1 < n2);
    if ((n1 < 0) != (n2 < 0)) {
      comp = !comp;
    }
    return comp;
  }

  public static boolean isZero(Object atom) {
    return TypesGen.isLong(atom) && 0L == TypesGen.asLong(atom);
  }

  public static Object lsh(byte bloq, int bits, Object atom) {
    int len = met(bloq, atom),
        big;

    if ( 0 == len ) {
      return 0L;
    }
    try {
      big = Math.addExact(bits, len);
    }
    catch (ArithmeticException e) {
      throw new Bail();
    }
    
    int[] sal = slaq(bloq, big);
    chop(bloq, 0, len, bits, sal, atom);

    return malt(sal);
  }
  
  public static Object malt(int[] words) {
    int bad = 0;

    for ( int i = words.length - 1; i >= 0; --i) {
      if ( words[i] == 0 ) {
        ++bad;
      }
      else {
        break;
      }
    }

    if ( bad > 0 ) {
      words = Arrays.copyOfRange(words, 0, words.length - bad);
    }

    if ( 0 == words.length ) {
      return 0L;
    }
    else if ( words != null && words.length > 2 ) {
      return words;
    }
    else if (words.length == 1) {
      return (long) words[0];
    }
    else {
      return ((long)words[1] << 32) | ((long)words[0] & 0xffffffffL);
    }
  }
  
  public static Object mas(Object atom) {
    int b = met(atom);
    if ( b < 2 ) {
      throw new Bail();
    }
    long c = 1 << (b - 1),
         d = 1 << (b - 2);
    Object e = sub(atom, c);
    return con(e, d);
  }
  
  public static int met(byte bloq, Object atom) {
    int gal, daz;

    if ( TypesGen.isLong(atom) ) {
      long v = (long) atom;
      if ( 0 == v ) {
        return 0;
      }
      else {
        int left = (int) v >>> 32;
        if ( left == 0 ) {
          gal = 0;
          daz = (int) v;
        }
        else {
          gal = 1;
          daz = left; 
        }
        gal = (v >>> 32) == 0 ? 0 : 1;
        daz = (int) v;
      }
    }
    else {
      int[] w = (int[]) atom;
      gal = w.length - 1;
      daz = w[gal];
    }
    
    switch (bloq) {
      case 0:
      case 1:
      case 2:
        int col = 32 - Integer.numberOfLeadingZeros(daz),
            bif = col + (gal << 5);

        return (bif + ((1 << bloq) - 1) >>> bloq);

      case 3:
        return (gal << 2)
          + ((daz >>> 24 != 0)
            ? 4
            : (daz >>> 16 != 0)
            ? 3
            : (daz >>> 8 != 0)
            ? 2
            : 1);

      case 4:
        return (gal << 1) + ((daz >>> 16 != 0) ? 2 : 1);

      default: {
        int gow = bloq - 5;
        return ((gal + 1) + ((1 << gow) - 1)) >>> gow;
      }
    }
  }
  
  public static int met(Object atom) {
    return met((byte)0, atom);
  }
  
  public static Object mod(int[] x, int[] y) {
    int cmp = compare(x,y);
    if ( cmp < 0 ) {
      return y;
    }
    else if ( 0 == cmp ) {
      return 0L;
    }
    else if ( 1 == y.length ) {
      int[] q = new int[x.length];
      return MPN.divmod_1(q, x, x.length, y[0]);
    }
    else {
      return malt(Arrays.copyOfRange(divmod(x,y), 0, y.length));
    }
  }
  
  public static long mod(long a, long b) {
    return a % b;
  }
  
  public static Object mod(Object a, Object b) {
    if ( TypesGen.isLong(a) && TypesGen.isLong(b) ) {
      return mod(TypesGen.asLong(a), TypesGen.asLong(b));
    }
    return mod(TypesGen.asImplicitIntArray(a), TypesGen.asImplicitIntArray(b));
  }
  
  public static int mug(Object atom) {
    int[] words = TypesGen.asImplicitIntArray(atom);
    return mug_words((int) 2166136261L, words.length, words);
  }

  private static int mug_words(int off, int nwd, int[] wod) {
    int has, out; 

    while ( true ) {
      has = mug_words_in(off, nwd, wod);
      out = Noun.mug_out(has);
      if ( 0 != out ) {
        return out;
      }
      ++off;
    }
  }

  private static int mug_words_in(int off, int nwd, int[] wod) {
    if (0 == nwd) {
      return off;
    }
    int i, x;
    for (i = 0; i < (nwd - 1); ++i) {
      x = wod[i];

      off = Noun.mug_fnv(off ^ ((x >>> 0)  & 0xff));
      off = Noun.mug_fnv(off ^ ((x >>> 8)  & 0xff));
      off = Noun.mug_fnv(off ^ ((x >>> 16) & 0xff));
      off = Noun.mug_fnv(off ^ ((x >>> 24) & 0xff));
    }
    x = wod[nwd - 1];
    if (x != 0) {
      off = Noun.mug_fnv(off ^ (x & 0xff));
      x >>>= 8;
      if (x != 0) {
        off = Noun.mug_fnv(off ^ (x & 0xff));
        x >>>= 8;
        if (x != 0) {
          off = Noun.mug_fnv(off ^ (x & 0xff));
          x >>>= 8;
          if (x != 0) {
            off = Noun.mug_fnv(off ^ (x & 0xff));
          }
        }
      }
    }
    return off;
  }
  
  public static Object mul(int[] x, int[] y) {
    int xlen = x.length,
        ylen = y.length;
    int[] dest = new int[xlen + ylen];
       
    if ( xlen < ylen ) {
      int zlen = xlen;
      int[] z = x;

      x = y;
      y = z;
      xlen = ylen;
      ylen = zlen;
    }

    MPN.mul(dest, x, xlen, y, ylen);
    return malt(dest);
  }
  
  public static long mul(long a, long b) throws ArithmeticException {
    return Math.multiplyExact(a, b);
  }
  
  public static Object mul(Object a, Object b) {
    if ( TypesGen.isLong(a) && TypesGen.isLong(b) ) {
      try {
        return mul(TypesGen.asLong(a), TypesGen.asLong(b));
      }
      catch (ArithmeticException e) {
      }
    }
    return mul(TypesGen.asImplicitIntArray(a), TypesGen.asImplicitIntArray(b));
  }

  public static Object peg(Object axis, Object to) {
    if ( equals(axis, 1L) ) {
      return axis;
    }
    else {
      int c = met(to),
          d = c - 1;
      long e = d << 1;

      Object f = sub(to, e),
             g = lsh((byte) 0, d, axis);
      
      return add(f, g);
    }
  }
  
  private static void reverseBytes(byte[] a) {
    int i, j;
    byte b;
    for (i = 0, j = a.length - 1; j > i; ++i, --j) {
      b = a[i];
      a[i] = a[j];
      a[j] = b;
    }
  }
  
  public static int[] slaq(byte bloq, int len) {
    int big = ((len << bloq) + 31) >>> 5;
    return new int[big];
  }

  public static Object stringToCord(String s) {
    try {
      return fromByteArray(s.getBytes("UTF-8"), LITTLE_ENDIAN);
    }
    catch (UnsupportedEncodingException e) {
      return null;
    }
  }

  public static int[] sub(int[] a, int[] b) {
    Square s = new Square(a, b);
    int[] dst = new int[s.len];
    int bor = MPN.sub_n(dst, s.x, s.y, s.len);
    if ( bor != 0 ) {
      throw new Bail();
    }
    return dst;
  }

  public static long sub(long a, long b) {
    if ( -1 == compare(a, b) ) {
      throw new Bail();
    }
    else {
      return a - b;
    }
  }
  
  public static Object sub(Object a, Object b) {
    if ( TypesGen.isLong(a) && TypesGen.isLong(b) ) {
      return sub(TypesGen.asLong(a), TypesGen.asLong(b));
    }
    else {
      int[] aa = TypesGen.asImplicitIntArray(a);
      int[] ba = TypesGen.asImplicitIntArray(b);
      return malt(sub(aa, ba));
    }
  }

  public static byte[] toByteArray(Object atom, boolean endian) {
    if (isZero(atom)) {
      return new byte[1];
    }
    int[]  wor = TypesGen.asImplicitIntArray(atom);
    int    bel = met((byte)3, atom);
    byte[] buf = new byte[bel];
    int    w, i, b;
    for (i = 0, b = 0;;) {
      w = wor[i++];

      buf[b++] = (byte) ((w & 0x000000FF) >>> 0);
      if (b >= bel) break;

      buf[b++] = (byte) ((w & 0x0000FF00) >>> 8);
      if (b >= bel) break;

      buf[b++] = (byte) ((w & 0x00FF0000) >>> 16);
      if (b >= bel) break;

      buf[b++] = (byte) ((w & 0xFF000000) >>> 24);
      if (b >= bel) break;
    }
    if (endian == BIG_ENDIAN) {
      reverseBytes(buf);
    }
    return buf;
  }

  public static String toString(Object atom) {
    return toString(atom, 10);
  }
  
  public static String toString(Object atom, int radix) {
    StringBuilder b = new StringBuilder();
    write(b, TypesGen.asImplicitIntArray(atom), radix);
    return b.toString();
  }
  
  public static void write(StringBuilder b, int[] cur, int radix) {
    int len   = cur.length,
        size  = len,
        i     = b.length(),
        j     = i - 1;
    
    cur = Arrays.copyOf(cur, cur.length);

    for(;;) {
      int dig = MPN.divmod_1(cur, cur, size, radix);
      b.append(Character.forDigit(dig, radix));
      ++j;
      if (cur[len-1] == 0) {
        if (--len == 0) {
          break;
        }
      }
    }

    for (; i < j; ++i, --j) {
      char t = b.charAt(j);
      b.setCharAt(j, b.charAt(i));
      b.setCharAt(i, t);
    }
  }

  public static Object cat(byte a, Object b, Object c) {
    int lew = met(a, b),
        ler = met(a, c),
        all = lew + ler;
    
    if ( 0 == all ) {
      return 0L;
    }
    else {
      int[] sal = slaq(a, all);

      chop(a, 0, lew, 0, sal, b);
      chop(a, 0, ler, lew, sal, c);

      return malt(sal);
    }
  }
  
  public static long expectLong(Object a) {
    try {
      return TypesGen.expectLong(a);
    }
    catch (UnexpectedResultException e) {
      throw new Bail();
    }
  }
  
  public static int expectInt(Object a) {
    long al = expectLong(a);
    int  ai = (int) al;
    if ( al != ai ) {
      throw new Bail();
    }
    return ai;
  }

  public static Object cut(byte a, Object b, Object c, Object d) {
    int ci, bi = expectInt(b);
    try {
      ci = expectInt(c);
    } 
    catch (Bail e) {
      ci = 0x7fffffff;
    }
    int len = met(a, d);

    if ( (0 == ci) || (bi >= len) ) {
      return 0L;
    }

    if ( (bi + ci) > len ) {
      ci = len - bi;
    }

    if ( (bi == 0) && (ci == len) ) {
      return d;
    }
    else {
      int[] sal = slaq(a, ci);
      chop(a,  bi, ci, 0, sal, d);
      return malt(sal);
    }
  }
}
