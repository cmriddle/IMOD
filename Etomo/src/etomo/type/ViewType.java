package etomo.type;

/**
 * <p>Description: View type definitions</p>
 *
 * <p>Copyright: Copyright (c) 2002</p>
 *
 * <p>Organization: Boulder Laboratory for 3D Fine Structure,
 * University of Colorado</p>
 *
 * @author $Author$
 *
 * @version $Revision$
 *
 * <p> $Log$
 * <p> Revision 1.1.2.1  2003/01/24 18:37:54  rickg
 * <p> Single window GUI layout initial revision
 * <p>
 * <p> Revision 1.1  2002/09/09 22:57:02  rickg
 * <p> Initial CVS entry, basic functionality not including combining
 * <p> </p>
 */
public class ViewType {
  public static final String rcsid =
    "$Id$";

  private final String name;

  private ViewType(String name) {
    this.name = name;
  }

  public static final ViewType SINGLE_VIEW = new ViewType("Single View");
  public static final ViewType MONTAGE = new ViewType("Montage");

  /**
   * Returns a string representation of the object.
   */
  public String toString() {
    return name;
  }

  /**
   * Takes a string representation of an ViewType type and returns the correct
   * static object.  The string is case insensitive.  Null is returned if the
   * string is not one of the possibilities from toString().
   */
  public static ViewType fromString(String name) {
    if (name.compareToIgnoreCase(SINGLE_VIEW.toString()) == 0) {
      return SINGLE_VIEW;
    }
    if (name.compareToIgnoreCase(MONTAGE.toString()) == 0) {
      return MONTAGE;
    }
    return null;
  }

}
