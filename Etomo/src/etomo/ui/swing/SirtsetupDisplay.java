package etomo.ui.swing;

import etomo.comscript.SirtsetupParam;

/**
* <p>Description: </p>
* 
* <p>Copyright: Copyright 2011</p>
*
* <p>Organization:
* Boulder Laboratory for 3-Dimensional Electron Microscopy of Cells (BL3DEMC),
* University of Colorado</p>
* 
* @author $Author$
* 
* @version $Revision$
* 
* <p> $Log$ </p>
*/
public interface SirtsetupDisplay {
  public static  final String  rcsid =  "$Id$";
  
  public boolean getParameters(SirtsetupParam param);
}
