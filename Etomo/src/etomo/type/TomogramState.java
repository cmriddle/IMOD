package etomo.type;

import java.util.Properties;

/**
* <p>Description: </p>
* 
* <p>Copyright: Copyright (c) 2002, 2003, 2004</p>
*
*<p>Organization:
* Boulder Laboratory for 3-Dimensional Electron Microscopy of Cells (BL3DEM),
* University of Colorado</p>
* 
* @author $Author$
* 
* @version $Revision$
* 
* <p> $Log$
* <p> Revision 1.1  2004/12/07 22:54:07  sueh
* <p> bug# 564 Contains state variables to be saved in the .edf file.
* <p> </p>
*/
public class TomogramState {
  public static  final String  rcsid =  "$Id$";
  
  private static final String groupString = "TomogramState";
  private static final String flippedString = "Flipped";
  private static final boolean defaultFlipped = false;
  
  boolean flipped;
  
  TomogramState() {
    reset();
  }
  
  void reset() {
    flipped = defaultFlipped;
  }
  
  void store(Properties props) {
    store(props, "");
  }
  
  void store(Properties props, String prepend) {
    prepend = createPrepend(prepend);
    String group = prepend + ".";
    props.setProperty(group + flippedString, Boolean.toString(flipped));
  }

  public void load(Properties props) {
    load(props, "");
  }

  public void load(Properties props, String prepend) {
    reset();
    prepend = createPrepend(prepend);
    String group = prepend + ".";
    flipped = Boolean.valueOf(
        props.getProperty(group + flippedString, Boolean
            .toString(defaultFlipped))).booleanValue();
  }
  
  public boolean equals(TomogramState that) {
    if (flipped != that.flipped) {
      return false;
    }
    return true;
  }
  
  public void setFlipped(boolean flipped) {
    this.flipped = flipped;
  }
  
  private static String createPrepend(String prepend) {
    if (prepend == "") {
      return groupString;
    }
    return prepend + "." + groupString;
  }

}
