package etomo.type;

import java.io.IOException;
import java.util.List;

import etomo.storage.LogFile;
import etomo.ui.Token;
import etomo.util.PrimativeTokenizer;

/**
 * <p>Description: </p>
 * 
 * <p>Copyright: Copyright 2006</p>
 *
 * <p>Organization:
 * Boulder Laboratory for 3-Dimensional Electron Microscopy of Cells (BL3DEMC),
 * University of Colorado</p>
 * 
 * @author $Author$
 * 
 * @version $Revision$
 * 
 * <p> $Log$
 * <p> Revision 1.8  2007/05/11 16:03:17  sueh
 * <p> bug# 964 Added getArray(List), which adds itself to the list, if it is not
 * <p> empty.
 * <p>
 * <p> Revision 1.7  2007/04/26 02:47:15  sueh
 * <p> bug# 964 Fixed problems with defaultValue.  Added ParsedArray.compact
 * <p> when empty array elements should not be displayed (lstThresholds).
 * <p>
 * <p> Revision 1.6  2007/04/19 21:43:48  sueh
 * <p> bug# 964 Added getRawString(int), moveElement(int, int), setRawString(int,
 * <p> float), setRawString(int, String).
 * <p>
 * <p> Revision 1.5  2007/04/13 21:51:17  sueh
 * <p> bug# 964 Not returning ConstEtomoNumber from ParsedElement, because it
 * <p> must be returned with a getDefaulted... function to be accurate.
 * <p> GetReferenceVolume is returning ParsedElement instead.
 * <p>
 * <p> Revision 1.4  2007/04/13 20:15:56  sueh
 * <p> bug# 964 Added setRawString(String).
 * <p>
 * <p> Revision 1.3  2007/04/09 21:01:33  sueh
 * <p> bug# 964 Placed createTokenizer(String) in the parent class, since it is used
 * <p> everywhere.
 * <p>
 * <p> Revision 1.2  2007/03/31 02:54:34  sueh
 * <p> bug# 964 Added isCollection().
 * <p>
 * <p> Revision 1.1  2007/03/30 23:42:20  sueh
 * <p> bug# 964 Abstract class to act as an interface for elements stored in ParsedElementList.
 * <p> </p>
 */
public abstract class ParsedElement {
  public static final String rcsid = "$Id$";

  public abstract String getRawString();

  public abstract Number getRawNumber();

  public abstract boolean isEmpty();

  public abstract void setRawString(String number);

  public abstract ParsedElement getElement(int index);

  public abstract void moveElement(int fromIndex, int toIndex);

  public abstract void setRawString(int index, float number);

  public abstract void setRawString(int index, String string);

  public abstract String getRawString(int index);

  abstract void fail();

  abstract Token parse(Token token, PrimativeTokenizer tokenizer);

  abstract int size();

  abstract String getParsableString();

  abstract boolean isCollection();

  abstract void setDebug(boolean debug);

  abstract boolean hasParsedNumberSyntax();

  abstract void setDefaultValue(int numberIndex, Integer[] defaultValueArray);

  abstract void removeElement(int index);

  abstract boolean isDefaultedEmpty();
  
  abstract boolean ge(int number);

  /**
   * Append non-null ParsedNumbers to parsedNumberArray.  Create
   * parsedNumberArray if parsedNumberArray == null and !this.isEmpty().
   * @param parsedNumberArray
   * @return parsedNumberArray
   */
  abstract List getArray(List parsedNumberArray);

  final PrimativeTokenizer createTokenizer(String value) {
    PrimativeTokenizer tokenizer = new PrimativeTokenizer(value);
    try {
      tokenizer.initialize();
    }
    catch (IOException e) {
      e.printStackTrace();
      fail();
    }
    catch (LogFile.ReadException e) {
      e.printStackTrace();
      fail();
    }
    return tokenizer;
  }
}
