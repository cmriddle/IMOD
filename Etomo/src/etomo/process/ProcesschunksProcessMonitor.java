package etomo.process;

import java.io.IOException;

import etomo.BaseManager;
import etomo.EtomoDirector;
import etomo.storage.LogFile;
import etomo.type.AxisID;
import etomo.type.ConstStringProperty;
import etomo.type.EtomoNumber;
import etomo.type.ProcessEndState;
import etomo.type.ProcessName;
import etomo.ui.ParallelProgressDisplay;
import etomo.ui.UIHarness;
import etomo.util.DatasetFiles;
import etomo.util.Utilities;

/**
 * <p>Description: </p>
 * 
 * <p>Copyright: Copyright (c) 2005</p>
 *
 * <p>Organization:
 * Boulder Laboratory for 3-Dimensional Electron Microscopy of Cells (BL3DEM),
 * University of Colorado</p>
 * 
 * @author $Author$
 * 
 * @version $Revision$
 */
class ProcesschunksProcessMonitor implements OutfileProcessMonitor,
    ParallelProcessMonitor {
  public static final String rcsid = "$Id$";

  private static final String TITLE = "Processchunks";

  static final String SUCCESS_TAG = "Finished reassembling";

  private static boolean debug = false;

  private final EtomoNumber nChunks = new EtomoNumber();
  private final EtomoNumber chunksFinished = new EtomoNumber();
  private final ParallelProgressDisplay parallelProgressDisplay;
  private final String rootName;
  private final String computerList;
  private final ProcessMessages messages = ProcessMessages
      .getInstanceForParallelProcessing();

  private String subdirName = null;
  private boolean setProgressBarTitle = false;//turn on to changed the progress bar title
  private boolean reassembling = false;
  private ProcessEndState endState = null;
  private LogFile commandsPipe = null;
  private long commandsPipeWriteId = LogFile.NO_ID;
  //private BufferedWriter commandsWriter = null;
  private boolean useCommandsPipe = true;
  //private BufferedReader bufferedReader = null;
  private LogFile processOutput = null;
  private long processOutputReadId = LogFile.NO_ID;
  private boolean processRunning = true;
  private boolean pausing = false;
  private boolean killing = false;
  //private File processOutputFile = null;
  private String pid = null;
  private boolean starting = true;
  private boolean finishing = false;
  private boolean stop = false;
  private boolean running = false;
  private boolean reconnect = false;

  final BaseManager manager;
  final AxisID axisID;

  ProcesschunksProcessMonitor(BaseManager manager, AxisID axisID,
      ParallelProgressDisplay parallelProgressDisplay, String rootName,
      String computerList) {
    this.manager = manager;
    this.axisID = axisID;
    this.parallelProgressDisplay = parallelProgressDisplay;
    this.rootName = rootName;
    this.computerList = computerList;
    debug = EtomoDirector.INSTANCE.getArguments().isDebug();
  }

  public static ProcesschunksProcessMonitor getReconnectInstance(
      BaseManager manager, AxisID axisID,
      ParallelProgressDisplay parallelProgressDisplay, ProcessData processData) {
    ProcesschunksProcessMonitor instance = new ProcesschunksProcessMonitor(
        manager, axisID, parallelProgressDisplay, processData
            .getSubProcessName(), null);
    instance.reconnect = true;
    return instance;
  }

  public final void setProcess(SystemProcessInterface process) {
  }

  public final boolean isRunning() {
    return running;
  }

  public final void stop() {
    stop = true;
  }

  public final void run() {
    running = true;
    //make sure commmandsPipe is deleted and enable its use
    try {
      deleteCommandsPipe(true);
    }
    catch (LogFile.FileException e) {
      UIHarness.INSTANCE.openMessageDialog(e.getMessage(),
          "Processchunks Error", axisID);
    }
    parallelProgressDisplay.msgStartingProcessOnSelectedComputers();
    nChunks.set(0);
    chunksFinished.set(0);
    initializeProgressBar();
    try {
      while (processRunning && !stop) {
        try {
          Thread.sleep(2000);
          if (updateState() || setProgressBarTitle) {
            updateProgressBar();
          }
        }
        catch (LogFile.ReadException e) {
          //File creation may be slow, so give this more tries.
          e.printStackTrace();
        }
      }
      closeProcessOutput();
    }
    catch (InterruptedException e) {
      endMonitor(ProcessEndState.DONE);
    }
    catch (LogFile.FileException e) {
      endMonitor(ProcessEndState.FAILED);
      e.printStackTrace();
    }
    // parallelProgressDisplay.setParallelProcessMonitor(null);
    //make sure commmandsPipe is deleted and disable its use
    try {
      deleteCommandsPipe(false);
    }
    catch (LogFile.FileException e) {
      e.printStackTrace();
    }
    parallelProgressDisplay.msgEndingProcess();
    running = false;
  }

  public final void msgLogFileRenamed() {
  }

  public final void endMonitor(ProcessEndState endState) {
    setProcessEndState(endState);
    processRunning = false;//the only place that this should be changed
  }

  public final String getPid() {
    return pid;
  }

  public final String getLogFileName() {
    try {
      return getProcessOutputFileName();
    }
    catch (LogFile.FileException e) {
      e.printStackTrace();
      return "";
    }
  }

  public final String getProcessOutputFileName() throws LogFile.FileException {
    createProcessOutput();
    return processOutput.getName();
  }

  /**
   * set end state
   * @param endState
   */
  public synchronized final void setProcessEndState(ProcessEndState endState) {
    this.endState = ProcessEndState.precedence(this.endState, endState);
  }

  public final ProcessEndState getProcessEndState() {
    return endState;
  }

  public final ProcessMessages getProcessMessages() {
    return messages;
  }

  public final String getSubProcessName() {
    return rootName;
  }

  public final void kill(SystemProcessInterface process, AxisID axisID) {
    try {
      writeCommand("Q");
      parallelProgressDisplay.msgKillingProcess();
      killing = true;
      setProgressBarTitle = true;
      if (starting) {
        //wait to see if processchunks is already starting chunks.
        try {
          Thread.sleep(2001);
        }
        catch (InterruptedException e) {
        }
        if (starting) {
          //processchunks hasn't started chunks and it won't because the "Q" has
          //been sent.  So it is safe to kill it in the usual way.
          if (process != null) {
            process.signalKill(axisID);
          }
        }
      }
    }
    catch (LogFile.WriteException e) {
      e.printStackTrace();
    }
    catch (LogFile.FileException e) {
      e.printStackTrace();
    }
  }

  public final void pause(SystemProcessInterface process, AxisID axisID) {
    try {
      writeCommand("P");
      parallelProgressDisplay.msgPausingProcess();
      pausing = true;
      setProgressBarTitle = true;
    }
    catch (LogFile.WriteException e) {
      e.printStackTrace();
    }
    catch (LogFile.FileException e) {
      e.printStackTrace();
    }
  }

  public final String getStatusString() {
    return chunksFinished + " of " + nChunks + " completed";
  }

  public final void drop(String computer) {
    if (computerList == null || computerList.indexOf(computer) != -1) {
      if (EtomoDirector.INSTANCE.getArguments().isDebug()) {
        System.err.println("try to drop " + computer);
      }
      try {
        writeCommand("D " + computer);
        setProgressBarTitle = true;
      }
      catch (LogFile.WriteException e) {
        e.printStackTrace();
      }
      catch (LogFile.FileException e) {
        e.printStackTrace();
      }
    }
  }

  public final boolean isProcessRunning() {
    return processRunning;
  }

  final void setSubdirName(String input) {
    subdirName = input;
  }

  final void setSubdirName(ConstStringProperty input) {
    if (input == null || input.isEmpty()) {
      subdirName = null;
    }
    else {
      subdirName = input.toString();
    }
  }

  final AxisID getAxisID() {
    return axisID;
  }

  final boolean isStarting() {
    return starting;
  }

  final boolean isFinishing() {
    return finishing;
  }

  synchronized void closeProcessOutput() {
    if (processOutput != null && processOutputReadId != LogFile.NO_ID) {
      processOutput.closeReader(processOutputReadId);
      processOutput = null;
    }
  }

  boolean updateState() throws LogFile.ReadException, LogFile.FileException {
    createProcessOutput();
    if (processOutputReadId == LogFile.NO_ID) {
      processOutputReadId = processOutput.openReader();
    }
    boolean returnValue = false;
    if (processOutputReadId == LogFile.NO_ID) {
      return returnValue;
    }
    String line;
    boolean failed = false;
    while ((line = processOutput.readLine(processOutputReadId)) != null) {
      line = line.trim();
      System.err.println(line);
      //get the first pid
      if (pid == null && line.startsWith("Shell PID:")) {
        String[] array = line.split("\\s+");
        if (array.length == 3) {
          pid = array[2].trim();
        }
      }
      if (debug) {
        System.err.println(line);
      }
      messages.addProcessOutput(line);
      if (messages.isError()) {
        //Set failure boolean but continue to add all the output lines to
        //messages.
        failed = true;
      }
      //If it got an error message, then it seems like the best thing to do is
      //stop processing.
      if (failed) {
        continue;
      }
      if (line.endsWith("to reassemble")) {
        //handle all chunks finished, starting the reassemble
        Utilities.timestamp(TITLE, rootName, "reassembling");
        //all chunks finished, turn off pause
        if (endState == ProcessEndState.PAUSED) {
          endState = null;
        }
        reassembling = true;
        setProgressBarTitle = true;
        returnValue = true;
      }
      else if (line.indexOf("BAD COMMAND IGNORED") != -1) {
        throw new IllegalStateException("Bad command sent to processchunks\n"
            + line);
      }
      else if (line.equals(SUCCESS_TAG)) {
        endMonitor(ProcessEndState.DONE);
      }
      else if (line
          .equals("When you rerun with a different set of machines, be sure to use")) {
        endMonitor(ProcessEndState.KILLED);
      }
      else if (line
          .equals("All previously running chunks are done - exiting as requested")) {
        endMonitor(ProcessEndState.PAUSED);
      }
      //A tcsh error causes processchunks to exit immediately.  Currently not
      //ending the monitor because we don't want the user to rerun processchunks
      //before all the chunk processes end.
      else if (line.indexOf("Syntax Error") != -1
          || line.indexOf("Subscript error") != -1
          || line.indexOf("Undefined variable") != -1
          || line.indexOf("Expression Syntax") != -1
          || line.indexOf("Subscript out of range") != -1
          || line.indexOf("Illegal variable name") != -1
          || line.indexOf("Variable syntax") != -1
          || line.indexOf("Badly placed (") != -1
          || line.indexOf("Badly formed number") != -1) {
        System.err.println("ERROR: Tcsh error in processchunks log");
        UIHarness.INSTANCE
            .openMessageDialog(
                "Unrecoverable error in processchunks.  Please contact the programmer.  Let the chunk processes complete before rerunning.",
                "Fatal Error");
      }
      else {
        String[] strings = line.split("\\s+");
        //set nChunks and chunksFinished
        if (strings.length > 2 && line.endsWith("DONE SO FAR")) {
          starting = false;
          if (!nChunks.equals(strings[2])) {
            nChunks.set(strings[2]);
            setProgressBarTitle = true;
          }
          chunksFinished.set(strings[0]);
          if (chunksFinished.equals(nChunks)) {
            finishing = true;
          }
          returnValue = true;
        }
        else if (strings.length > 1) {
          if (line.startsWith("Dropping")) {
            String failureReason;
            if (line.indexOf("it cannot cd to") != -1) {
              failureReason = "cd failed";
            }
            else if (line.indexOf("cannot connect") != -1) {
              failureReason = "connect failed";
            }
            else if (line.indexOf("it cannot run IMOD commands") != -1) {
              failureReason = "run error";
            }
            else if (line.indexOf("it failed (with time out)") != -1) {
              failureReason = "chunk timed out";
            }
            else if (line.indexOf("it failed (with chunk error)") != -1) {
              failureReason = "chunk error";
            }
            else {
              failureReason = "unknown error";
            }
            //handle a dropped CPU
            parallelProgressDisplay.msgDropped(strings[1], failureReason);
          }
          //handle commandsPipeWriteIda finished chunk
          else if (strings[1].equals("finished")) {
            parallelProgressDisplay.addSuccess(strings[3]);
          }
          //handle a failed chunk
          else if (strings[1].equals("failed")) {
            parallelProgressDisplay.addRestart(strings[3]);
          }
        }
      }
    }
    if (failed) {
      endMonitor(ProcessEndState.FAILED);
      returnValue = false;
    }
    return returnValue;
  }

  void updateProgressBar() {
    if (setProgressBarTitle) {
      setProgressBarTitle = false;
      setProgressBarTitle();
    }
    manager.getMainPanel().setProgressBarValue(chunksFinished.getInt(),
        getStatusString(), axisID);
  }

  private void initializeProgressBar() {
    setProgressBarTitle();
    if (reconnect) {
      manager.getMainPanel().setProgressBarValue(chunksFinished.getInt(),
          "Reconnecting...", axisID);
    }
    else {
      manager.getMainPanel().setProgressBarValue(chunksFinished.getInt(),
          "Starting...", axisID);
    }
  }

  private final void setProgressBarTitle() {
    StringBuffer title = new StringBuffer(TITLE);
    if (rootName != null) {
      title.append(" " + rootName);
    }
    if (reassembling) {
      title.append(":  reassembling");
    }
    else if (killing) {
      title.append(" - killing:  exiting current chunks");
    }
    else if (pausing) {
      title.append(" - pausing:  finishing current chunks");
    }
    manager.getMainPanel().setProgressBar(title.toString(), nChunks.getInt(),
        axisID, !reassembling && !killing, ProcessName.PROCESSCHUNKS);
  }

  /**
   * make sure the commandsPipe file is deleted and enable/disable its use
   * synchronized with createCommandsWriter
   * synchronization is for useCommandsPipe and commmandsPipe
   * @param startup
   */
  private final synchronized void deleteCommandsPipe(boolean enable)
      throws LogFile.FileException {
    if (!enable) {
      //turn off useCommandsPipe to prevent use of commandsPipe
      useCommandsPipe = false;
    }
    //delete the commands pipe even if it was never created (just to be sure)
    if (commandsPipe == null) {
      commandsPipe = LogFile.getInstance(manager.getPropertyUserDir(),
          DatasetFiles.getCommandsFileName(subdirName, rootName));
    }
    if (commandsPipeWriteId != LogFile.NO_ID) {
      commandsPipe.closeWriter(commandsPipeWriteId);
      commandsPipeWriteId = LogFile.NO_ID;
    }
    commandsPipe.delete();
    if (enable) {
      //turn on useCommandsPipe to enable use of commandsPipe
      useCommandsPipe = true;
    }
    else {
      commandsPipe = null;
    }
  }

  /**
   * create commandsWriter if necessary, write command, add newline, flush
   * @param command
   * @throws IOException
   */
  private final void writeCommand(String command)
      throws LogFile.WriteException, LogFile.FileException {
    if (!useCommandsPipe) {
      return;
    }
    if (commandsPipe == null) {
      commandsPipe = LogFile.getInstance(manager.getPropertyUserDir(),
          DatasetFiles.getCommandsFileName(subdirName, rootName));
    }
    if (commandsPipeWriteId == LogFile.NO_ID) {
      commandsPipeWriteId = commandsPipe.openWriter();
    }
    if (commandsPipeWriteId == LogFile.NO_ID) {
      return;
    }
    commandsPipe.write(command, commandsPipeWriteId);
    commandsPipe.newLine(commandsPipeWriteId);
    commandsPipe.flush(commandsPipeWriteId);
  }

  /**
   * make sure process output file is new and set processOutputFile.  This
   * function should be first run before the process starts.
   */
  private final synchronized void createProcessOutput()
      throws LogFile.FileException {
    if (processOutput == null) {
      processOutput = LogFile.getInstance(manager.getPropertyUserDir(),
          DatasetFiles.getOutFileName(manager, subdirName,
              ProcessName.PROCESSCHUNKS.toString(), axisID));
      //Don't remove the file if this is a reconnect.
      if (!reconnect) {
        //Avoid looking at a file from a previous run.
        processOutput.backup();
      }
    }
  }
}
/**
 * <p> $Log$
 * <p> Revision 1.39  2008/05/16 22:44:16  sueh
 * <p> bug# 1109 Added getSubProcess so that the process that processchunks
 * <p> is running can be saved in ProcessData.
 * <p>
 * <p> Revision 1.38  2008/05/05 19:57:58  sueh
 * <p> bug# 1108 In run, when catching a read exception do not leave the while
 * <p> loop.
 * <p>
 * <p> Revision 1.37  2008/01/31 20:19:06  sueh
 * <p> bug# 1055 throwing a FileException when LogFile.getInstance fails.
 * <p>
 * <p> Revision 1.36  2008/01/23 21:10:50  sueh
 * <p> Removed print statements.
 * <p>
 * <p> Revision 1.35  2008/01/14 21:31:47  sueh
 * <p> bug# 1050 Added stop() and isRunning() to allow ProcessMonitor classes to work
 * <p> with ReconnectProcess.  Added boolean reconnect, which prevents the process
 * <p> output file from being deleted.
 * <p>
 * <p> Revision 1.34  2007/12/26 22:14:38  sueh
 * <p> bug# 1052 Moved argument handling from EtomoDirector to a separate class.
 * <p>
 * <p> Revision 1.33  2007/12/10 22:29:11  sueh
 * <p> bug# 1041 Removed subdirName from the constructor because it is optional.
 * <p>
 * <p> Revision 1.32  2007/11/06 19:25:21  sueh
 * <p> bug# 1-47 Allowed processchunks to be executed in a subdirectory.
 * <p>
 * <p> Revision 1.31  2007/09/27 20:27:11  sueh
 * <p> bug# 1044 Giving a message that the process is ending at the end of the run()
 * <p> function so that the "Use a cluster" checkbox can be turned back on.  No longer
 * <p> setting the parallel process monitor in the parallel progress display since it is
 * <p> never used.
 * <p>
 * <p> Revision 1.30  2007/09/07 00:19:14  sueh
 * <p> bug# 989 Using a public INSTANCE to refer to the EtomoDirector singleton
 * <p> instead of getInstance and createInstance.
 * <p>
 * <p> Revision 1.29  2007/08/29 21:43:14  sueh
 * <p> bug# 1041 Set BaseState.KilledProcesschunksProcessName when a kill or
 * <p> pause is done.
 * <p>
 * <p> Revision 1.28  2007/02/22 20:36:51  sueh
 * <p> bug# 964 Printing processchunks output to the etomo_err.log for now.
 * <p>
 * <p> Revision 1.27  2006/12/02 04:38:06  sueh
 * <p> bug# 944 Made this class a parent of ProcesschunksVolcombineMonitor.
 * <p>
 * <p> Revision 1.26  2006/12/01 00:58:06  sueh
 * <p> bug# 937 Don't have to set process in monitor because its being passed to the
 * <p> kill function.  Made endMonitor public so that the process could stop the monitor.
 * <p>
 * <p> Revision 1.25  2006/11/30 20:10:28  sueh
 * <p> bug# 937 In kill(), kill processchunk chunks with signalKill, if it hasn't started yet.
 * <p>
 * <p> Revision 1.24  2006/11/08 00:24:05  sueh
 * <p> bug# 935  createProcessOutput():  backup the old process output file instead of
 * <p> deleting it.
 * <p>
 * <p> Revision 1.23  2006/10/24 21:38:10  sueh
 * <p> bug# 947 Passing the ProcessName to AxisProcessPanel.
 * <p>
 * <p> Revision 1.22  2006/10/18 15:42:06  sueh
 * <p> bug# 929  updateState():  Improving failure reasons.
 * <p>
 * <p> Revision 1.21  2006/10/11 10:10:14  sueh
 * <p> bug# 931 Managing the commands pipe and the process output with LogFile so
 * <p> that the file access problem which appears in Windows will show up in Linux.
 * <p>
 * <p> Revision 1.20  2006/10/10 12:06:40  sueh
 * <p> bug# 931 deleteCommandPipe():  throwing an exception if can't delete
 * <p> the .cmds file.
 * <p>
 * <p> Revision 1.19  2006/09/25 16:36:18  sueh
 * <p> bug# 931 Added empty function msgLogFileRenamed().
 * <p>
 * <p> Revision 1.18  2006/06/05 16:30:51  sueh
 * <p> bug# 766 Implementing OutfileProcessMonitor - adding getPid.  Getting the pid
 * <p> from the output file.
 * <p>
 * <p> Revision 1.17  2006/01/31 20:45:04  sueh
 * <p> bug# 521 Added the process to combine monitor.  This allows the last
 * <p> ProcessResultDisplay used by the monitor to be assigned to the process.
 * <p>
 * <p> Revision 1.16  2006/01/06 23:17:08  sueh
 * <p> bug# 795 Fixed a bug in updateState where it ends the monitor as soon as
 * <p> it sees an error message.  This means that the error messages popped
 * <p> up to the user where incomplete.  When an error is found, collect all the
 * <p> messages and then end the monitor.
 * <p>
 * <p> Revision 1.15  2005/11/29 22:24:10  sueh
 * <p> bug# 744 Stop putting processchunks output into the error log, since its
 * <p> going into the .out file.
 * <p>
 * <p> Revision 1.14  2005/11/19 02:36:13  sueh
 * <p> bug# 744 Converted to a detached program.  Managing the process
 * <p> output file.  No longer using the process.  Reading the process output file
 * <p> to update the program display.  Handling kills and pauses in
 * <p> updateState().  Handling errors and chunk errors with ProcessMessages.
 * <p>
 * <p> Revision 1.13  2005/11/04 00:53:00  sueh
 * <p> fixed pausing message
 * <p>
 * <p> Revision 1.12  2005/10/21 19:55:54  sueh
 * <p> bug# 742 Sending text from standard error to the error log.
 * <p>
 * <p> Revision 1.11  2005/09/27 21:16:18  sueh
 * <p> bug# 532 Temporarily printing the processchunks output to the error log
 * <p> without the debug setting.
 * <p>
 * <p> Revision 1.10  2005/09/10 01:52:41  sueh
 * <p> bug# 532 Setting the reason for all dropped computers to "not responding"
 * <p> to distinguish it from load average timeouts.
 * <p>
 * <p> Revision 1.9  2005/09/09 21:42:22  sueh
 * <p> bug# 532 Throwing an exception if a command is sent that processchunks
 * <p> doesn't recognize.
 * <p>
 * <p> Revision 1.8  2005/09/07 20:51:06  sueh
 * <p> bug# 532 Added commandsPipe and commandsWriter to send commands
 * <p> for processchunks to a file.
 * <p>
 * <p> Revision 1.7  2005/09/01 17:54:58  sueh
 * <p> bug# 532 handle multiple drop reasons.  Fix drop function:  use interrupt
 * <p> signal only when the computer is recognized.
 * <p>
 * <p> Revision 1.6  2005/08/30 22:41:45  sueh
 * <p> bug# 532 Added error log print statement to drop().
 * <p>
 * <p> Revision 1.5  2005/08/30 18:50:23  sueh
 * <p> bug# 532 Added drop(String computer).  Added dropComputer which is set
 * <p> while waiting for the interrupt to happen.  Added computerList so that
 * <p> the monitor can avoid interrupting for computers that processchunks isn't
 * <p> using.  Added code to updateState() to send "D computer_name" to
 * <p> processchunks.
 * <p>
 * <p> Revision 1.4  2005/08/27 22:31:47  sueh
 * <p> bug# 532 In updateState() look for CHUNK ERROR: and save the most
 * <p> recent one found.  Return the last chunk error message with
 * <p> getErrorMessage().
 * <p>
 * <p> Revision 1.3  2005/08/22 17:03:43  sueh
 * <p> bug# 532 Added getStatusString() to implement ProcessMonitor.  The
 * <p> status string is used to add more information to the progress bar when
 * <p> the process ends.  It is currently being used only for pausing
 * <p> processchunks.  Added "pausing" string to progress title as soon as a
 * <p> pause is detected since pausing takes a lot of time.
 * <p>
 * <p> Revision 1.2  2005/08/15 18:23:54  sueh
 * <p> bug# 532  Added kill and pause functions to implement ProcessMonitor.
 * <p> Both kill and pause signal interrupt.  Change updateState to handle the
 * <p> interrupt message and send the correct string, based on whether a kill or
 * <p> a pause was requested.
 * <p>
 * <p> Revision 1.1  2005/08/04 19:46:15  sueh
 * <p> bug# 532 Class to monitor processchunks.  Monitors the standard output.
 * <p> Sends updates to the progress panel and to a parallel process display.
 * <p> </p>
 */
