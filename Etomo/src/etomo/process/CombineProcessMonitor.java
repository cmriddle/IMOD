package etomo.process;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import etomo.ApplicationManager;
import etomo.comscript.CombineComscriptState;
import etomo.type.AxisID;
import etomo.util.Utilities;


/**
 * <p>
 * Description: Provides a threadable class to execute IMOD com scripts in the
 * background.  An instance of this class can be run only once.
 * </p>
 * 
 * <p>Copyright: Copyright (c) 2004</p>
 * 
 * <p>Organization:
 * Boulder Laboratory for 3-Dimensional Electron Microscopy of Cells (BL3DEM),
 * University of Colorado</p>
 * 
 * @author $$Author$$
 * 
 * @version $$Revision$$
 * 
 * <p> $$Log$
 * <p> $Revision 1.4  2004/08/23 23:35:45  sueh
 * <p> $bug# 508 made this class more like LogFileProcessMonitor.
 * <p> $Calling interrupt on child monitors and this monitor to make
 * <p> $run() complete faster
 * <p> $
 * <p> $Revision 1.3  2004/08/20 21:41:53  sueh
 * <p> $bug# 508 CombineComscriptState match string is now static.
 * <p> $Improved selfTest()
 * <p> $
 * <p> $Revision 1.2  2004/08/19 20:09:01  sueh
 * <p> $bug# 508 Made finding the .com file names more robust.  After 
 * <p> $finding the string "running" or "Running", find a string that 
 * <p> $matched a regular expression generated by 
 * <p> $CombineComscriptState.
 * <p> $Changed:
 * <p> $getCurrentSection()
 * <p> $setCurrentChildCommand(String comscriptName)
 * <p> $
 * <p> $Revision 1.1  2004/08/19 01:59:11  sueh
 * <p> $bug# 508 Watches combine.com.  Runs monitors for child .com
 * <p> $processes that have monitors.  For other child .com processes, starts
 * <p> $a progress bar and displays the process name.  Uses the combine.log
 * <p> $file to figure out when child process is running.  Uses
 * <p> $CombineComscriptState to figure out which child .com processes are
 * <p> $valid.  Also uses CombineComscriptState to know which dialog pane
 * <p> $to tell ApplicationManager to set.  Does not inherit
 * <p> $LogFileProcessMonitor.  Figures out when the process ends by
 * <p> $watching the combine.log or by setKill() being called by another object.
 * <p> $Provides information to other objects about the status of the combine
 * <p> $process.
 * <p> $$ </p>
 */
public class CombineProcessMonitor implements Runnable, BackgroundProcessMonitor {
  public static final String rcsid = "$$Id$$";
  public static final String COMBINE_LABEL = "Combine";
  private static final long SLEEP = 100;
  
  private ApplicationManager applicationManager = null;
  private AxisID axisID = null;
  private BufferedReader logFileReader = null;
  private long sleepCount = 0;
  
  //if processRunning is false at any time before the process ends, it can
  //cause wait loops to end prematurely.  This is because the wait loop can
  //start very repidly for a background process.
  //See BackgroundSystemProgram.waitForProcess().
  private boolean processRunning = true;
  
  private File logFile = null;
  private LogFileProcessMonitor childMonitor = null;
  Thread childThread = null;
  private boolean success = false;
  private CombineComscriptState combineComscriptState = null;
  private int currentCommandIndex = CombineComscriptState.NULL_INDEX;
  
  private static final int CONSTRUCTED_STATE = 1;
  private static final int WAITED_FOR_LOG_STATE = 2;
  private static final int RAN_STATE = 3;
  private boolean selfTest = false;
  private Thread runThread = null;
  
  
  /**
   * @param applicationManager
   * @param axisID
   */
  public CombineProcessMonitor(ApplicationManager applicationManager,
    AxisID axisID, CombineComscriptState combineComscriptState) {
    this.applicationManager = applicationManager;
    this.axisID = axisID;
    this.combineComscriptState = combineComscriptState;
    selfTest = ApplicationManager.isSelfTest();
    runSelfTest(CONSTRUCTED_STATE);
  }

  /**
   * returns false if the process has stopped, after giving run() a chance to
   * finish
   */
  public boolean isProcessRunning() {
    if (!processRunning) {
      //give run a chance to finish
      try {
        Thread.sleep(SLEEP);
      }
      catch (InterruptedException e) {
      } 
      return false;
    }
    return true;
  }
  
  /**
   * true if finished successfully
   */
  public boolean isSuccessful() {
    return success;
  }
  
  /**
   * called when the process is killed by the user
   */
  public void kill() {
    endMonitor();
  }
  
  private void initializeProgressBar() {
    applicationManager.startProgressBar(COMBINE_LABEL, axisID);
    return;
  }

  /**
   * get each .com file run by combine.com
   * @throws NumberFormatException
   * @throws IOException
   */
  private void getCurrentSection()
    throws NumberFormatException, IOException {
    String line;
    String matchString = CombineComscriptState.getComscriptMatchString();
    while ((line = logFileReader.readLine()) != null) {
      int index = -1;
      if ((line.indexOf("running ") != -1 || line.indexOf("Running ") != -1) &&
          line.matches(matchString)) {
        String[] fields = line.split("\\s+");
        for (int i = 0; i < fields.length; i++) {
          if (fields[i].matches(matchString)) {
            String comscriptName = fields[i];
            setCurrentChildCommand(comscriptName);
            runCurrentChildMonitor();
          }          
        }
      }
      else if (line.startsWith("ERROR:")) {
        endMonitor();
      }
      else if (
        line.startsWith(CombineComscriptState.getSuccessText())) {
        success = true;
        endMonitor();

      }
    }
  }
  
  /**
   * get current .com file run by combine.com
   * run the monitor associated with the current .com file, if these is one
   * @param comscriptName
   */
  private void setCurrentChildCommand(String comscriptName) {
    String childCommandName =
      comscriptName.substring(0, comscriptName.indexOf(".com"));
    applicationManager.progressBarDone(axisID);

    if (childCommandName.equals(combineComscriptState.getCommand(
        CombineComscriptState.PATCHCORR_INDEX))) {
      endCurrentChildMonitor();
      applicationManager.showPane(CombineComscriptState.COMSCRIPT_NAME,
          CombineComscriptState.getDialogPane(
          CombineComscriptState.PATCHCORR_INDEX));
      childMonitor = new PatchcorrProcessWatcher(applicationManager, axisID);
    }
    else if (
      childCommandName.equals(combineComscriptState.getCommand(
          CombineComscriptState.VOLCOMBINE_INDEX))) {
      endCurrentChildMonitor();
      applicationManager.showPane(CombineComscriptState.COMSCRIPT_NAME,
          CombineComscriptState.getDialogPane(
          CombineComscriptState.VOLCOMBINE_INDEX));
      childMonitor = new VolcombineProcessMonitor(applicationManager, axisID);
    }
    else {
      startProgressBar(childCommandName);
    }
  }
  
  /**
   * run the monitor associated with the current .com file run by combine.com
   *
   */
  private void runCurrentChildMonitor() {
    if (childMonitor == null) {
      return;
    }
    childMonitor.setLastProcess(false);
    childThread = new Thread(childMonitor);
    childThread.start();
  }
  
  /**
   * stop the current monitor associated with the current .com file run by
   * combine.com
   *
   */
  private void endCurrentChildMonitor() {
    if (childMonitor != null) {
      childMonitor.haltProcess(childThread);
    }
    childMonitor = null;
    childThread = null;
  }

  /**
   * end this monitor
   *
   */
  private void endMonitor() {
    endCurrentChildMonitor();
    applicationManager.progressBarDone(axisID);
    if (runThread != null) {
      runThread.interrupt();
      runThread = null;
    }
    processRunning = false;
  }
  
  /**
   * Start  a progress bar for the current .com file run by combine.com.
   * Used when there is no monitor available for the child process
   * @param childCommandName
   */
  private void startProgressBar(String childCommandName) {
    int commandIndex = CombineComscriptState.getCommandIndex(childCommandName);
    if (commandIndex == CombineComscriptState.NULL_INDEX) {
      //must be a command that is not monitored
      return;
    }
    endCurrentChildMonitor();
    applicationManager.showPane(CombineComscriptState.COMSCRIPT_NAME,
      CombineComscriptState.getDialogPane(commandIndex));
    applicationManager.startProgressBar(COMBINE_LABEL + ": " + childCommandName,
      axisID);
  }

  /**
   * Get log file.  Initialize progress bar.  Loop until processRunning is 
   * turned off or there is a timeout.  Call getCurrentSection for each loop.
   * After loop, turn off the monitor if that hasn't been done already.
   */
  public void run() {
    runThread = Thread.currentThread();
    initializeProgressBar();
    //  Instantiate the logFile object
    String logFileName = CombineComscriptState.COMSCRIPT_NAME + ".log";
    logFile = new File(System.getProperty("user.dir"), logFileName);

    boolean processRunning = true;
    try {
      //  Wait for the log file to exist
      waitForLogFile();
      initializeProgressBar();

      while (processRunning) {
        Thread.sleep(SLEEP);
        getCurrentSection();
      }
    }
    catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    catch (InterruptedException e) {
      processRunning = false;
    }
    catch (NumberFormatException e) {
      e.printStackTrace();
    }
    catch (IOException e) {
      e.printStackTrace();
    }

    //  Close the log file reader
    try {
      Utilities
        .debugPrint("LogFileProcessMonitor: Closing the log file reader for "
            + logFile.getAbsolutePath());
      if (logFileReader != null) {
        logFileReader.close();
      }
    }
    catch (IOException e1) {
      e1.printStackTrace();
    }
    endMonitor();
    runSelfTest(RAN_STATE);
  }

  /**
   * Wait for the process to start and the appropriate log file to be created 
   * @return a buffered reader of the log file
   */
  private void waitForLogFile() throws InterruptedException, 
      FileNotFoundException {
    if (logFile == null) {
      throw new NullPointerException("logFile");
    }
    boolean newLogFile = false;
    while (!newLogFile) {
      // Check to see if the log file exists that signifies that the process
      // has started
      if (logFile.exists()) {
        newLogFile = true;
      }
      else {
        Thread.sleep(SLEEP);
      }
    }
    //  Open the log file
    logFileReader = new BufferedReader(new FileReader(logFile));
    runSelfTest(WAITED_FOR_LOG_STATE);
  }

  /**
   * Runs selfTest(int) when selfTest is set
   * @param selfTest
   * @param state
   */
  private void runSelfTest(int state) {
    if (!selfTest) {
      return;
    }
    selfTest(state);
  }
  
  /**
   * test for incorrect member variable settings.
   * @param state
   */
  public void selfTest(int state) {
    String stateString = null;
    switch (state) {
      case CONSTRUCTED_STATE :
        stateString = "After construction:  ";  
        if (axisID == null) {
          throw new NullPointerException(stateString 
              + "AxisID should not be null");
        }               
        if (combineComscriptState == null) {
          throw new NullPointerException(stateString
              + "CombineComscriptState should not be null");
        }          
        if (!processRunning) {
          throw new IllegalStateException(stateString 
              + "ProcessRunning must be true");
        }               
            
        break;

      case WAITED_FOR_LOG_STATE :
        stateString = "After waitForLogFile():  ";  
        if (logFile.exists() && sleepCount != 0) {
          throw new IllegalStateException(stateString 
              + "The sleepCount should be reset when the log file is found.  "
              + "sleepCount=" + sleepCount);
        }              
            
        break;

      case RAN_STATE :
        stateString = "After run():  ";  
        if (processRunning) {
          throw new IllegalStateException(stateString 
              + "ProcessRunning should be false.");
        }               

        break;
       
      default :
        throw new IllegalStateException("Unknown state.  state=" + state);
    }
  }

}
