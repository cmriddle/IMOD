package etomo.comscript;

import java.util.ArrayList;

import etomo.ApplicationManager;
import etomo.BaseManager;
import etomo.EtomoDirector;
import etomo.process.ProcessMessages;
import etomo.process.SystemProgram;
import etomo.type.AxisID;
import etomo.type.ProcessName;

/**
* <p>Description: </p>
* 
* <p>Copyright: Copyright 2012</p>
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
public class BatchruntomoParam {
  public static final String rcsid = "$Id:$";

  private final ArrayList command = new ArrayList();

  private final BaseManager manager;

  private StringBuffer commandLine = null;
  private SystemProgram batchruntomo = null;
  private int exitValue = -1;

  public BatchruntomoParam(BaseManager manager) {
    this.manager = manager;
  }

  public boolean setup() {
    // Create a new SystemProgram object for copytomocom, set the
    // working directory and stdin array.
    // Do not use the -e flag for tcsh since David's scripts handle the failure
    // of commands and then report appropriately. The exception to this is the
    // com scripts which require the -e flag. RJG: 2003-11-06
    command.add("python");
    command.add("-u");
    command.add(ApplicationManager.getIMODBinPath() + ProcessName.BATCHRUNTOMO);
    command.add("-validation");
    command.add("1");
    command.add("-directive");
    command.add(EtomoDirector.INSTANCE.getArguments().getDirective().getAbsolutePath());
    batchruntomo = new SystemProgram(manager, manager.getPropertyUserDir(), command,
        AxisID.ONLY);
    return true;
  }

  /**
   * Return the current command line string
   * 
   * @return
   */
  public String getCommandLine() {
    if (batchruntomo == null) {
      return "";
    }
    return batchruntomo.getCommandLine();
  }

  /**
   * Execute the copytomocoms script
   * 
   * @return @throws
   *         IOException
   */
  public int run() {
    if (batchruntomo == null) {
      return -1;
    }
    int exitValue;

    // Execute the script
    batchruntomo.run();
    exitValue = batchruntomo.getExitValue();
    return exitValue;
  }

  public String getStdErrorString() {
    if (batchruntomo == null) {
      return "ERROR: Batchruntomo is null.";
    }
    return batchruntomo.getStdErrorString();
  }

  public String[] getStdError() {
    if (batchruntomo == null) {
      return new String[] { "ERROR: Batchruntomo is null." };
    }
    return batchruntomo.getStdError();
  }

  /**
   * returns a String array of warnings - one warning per element
   * make sure that warnings get into the error log
   * @return
   */
  public ProcessMessages getProcessMessages() {
    if (batchruntomo == null) {
      return null;
    }
    return batchruntomo.getProcessMessages();
  }
}
