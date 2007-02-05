package etomo.storage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Properties;

import etomo.type.AxisID;
import etomo.type.ProcessName;
import etomo.util.DatasetFiles;
import etomo.util.Utilities;

/**
 * <p>Description: Class which controls the opening and closing of a log file.
 * This class is necessary because working with log files on Windows has
 * become unreliable and a separate class was needed to ensure that the log
 * files are not left open.
 * 
 * This class contains a semaphore called Lock, which contains three types of
 * locks:  Read, Write, and File.  File is the most exclusive.  Only one File
 * lock can exists at a time and it can't coexist with any other kind of lock.  
 * Only one Write lock can exist at a time, but it can coexist with Read locks.
 * Multiple Read locks can exist at a time.
 * 
 * LogFile is an N'ton and stores its instances in a Hashtable, which is a
 * synchronized class.
 * 
 * Log File is a syncrhonized class:  all of its public and package-level
 * functions are synchronized, except getInstance functions (createInstance() is
 * synchronized).</p>
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
 */

public final class LogFile {
  public static final String rcsid = "$Id$";

  public static final long NO_ID = -1;
  public static final long NO_WAIT_LIMIT = -1;
  private static final String PUBLIC_EXCEPTION_MESSAGE = "\nPlease inform the software developer.";

  private static Hashtable logFileHashTable = null;
  private static ReaderList readerList = null;

  private final Lock lock = new Lock();

  private final String fileAbsolutePath;

  private File file = null;
  private File backupFile = null;
  private FileWriter fileWriter = null;
  private BufferedWriter bufferedWriter = null;
  private FileInputStream inputStream = null;
  private FileOutputStream outputStream = null;
  private boolean backedUp = false;

  private LogFile(File file) {
    this.fileAbsolutePath = file.getAbsolutePath();
  }

  public String toString() {
    return "\n[fileAbsolutePath=" + fileAbsolutePath + ",lock=" + lock + "]";
  }

  /**
   * Get an instance of LogFile based on a key constructed from parameters
   * describing the log file.
   * @param userDir
   * @param axisID
   * @param processName
   * @return retrieved instance
   */
  public static LogFile getInstance(String userDir, AxisID axisID,
      ProcessName processName) {
    return getInstance(userDir, axisID, processName.toString());
  }

  public static LogFile getInstance(String userDir, AxisID axisID, String name) {
    return getInstance(userDir, name + axisID.getExtension()
        + DatasetFiles.LOG_EXT);
  }

  public static LogFile getInstance(String userDir, String fileName) {
    return getInstance(new File(userDir, fileName));
  }

  public static LogFile getInstance(File file) {
    LogFile logFile;
    String key = makeKey(file);
    if (logFileHashTable == null
        || (logFile = (LogFile) logFileHashTable.get(key)) == null) {
      //the instance doesn't exist - create it
      logFile = createInstance(file, key);
    }
    return logFile;
  }

  /**
   * For testing.  Removes all instances of LogFile.
   */
  synchronized static void reset() {
    logFileHashTable = null;
    readerList = null;
  }

  /**
   * Check for the existance of the instance, because another thread could have
   * created before this thread called createInstance().  If the instance
   * isn't there, create an instance of LogFile and a key and add them to
   * logFileHashTable.
   * @param userDir
   * @param fileName
   * @param key
   * @return created instance
   */
  private static synchronized LogFile createInstance(File file, String key) {
    LogFile logFile;
    //make sure that the instance wasn't created by another thread
    if (logFileHashTable != null
        && (logFile = (LogFile) logFileHashTable.get(key)) != null) {
      return logFile;
    }
    //create the instance
    logFile = new LogFile(file);
    if (logFileHashTable == null) {
      logFileHashTable = new Hashtable();
    }
    //save the instance
    logFileHashTable.put(key, logFile);
    return logFile;
  }

  /**
   * Try to do a backup and set backedUp to true.  This prevents more then one backup to
   * be done on the file during the lifetime of the instance.  This prevents the
   * loss of data from a previous session because of too many backups being
   * done.
   * If backup() throws an exception, set backedUp to false and rethrow the
   * exception.
   * @return true if backup() was called and was successful
   * @throws FileException
   */
  public synchronized boolean backupOnce() throws FileException {
    if (backedUp) {
      return false;
    }
    boolean backupResult = false;
    try {
      backupResult = backup();
      backedUp = true;
    }
    catch (FileException e) {
      throw e;
    }
    return backupResult;
  }

  /**
   * Delete the current backup file and rename the current file to be the new
   * backup file.  The current backup file will not be deleted unless the
   * current file exists.
   * Will not backup if backedUp is true (doesn't set backedUp)
   * @return true if backup() successful or already backed up
   * @throws FileException
   */
  public synchronized boolean backup() throws FileException {
    if (backedUp) {
      return false;
    }
    createFile();
    if (!file.exists()) {
      return false;
    }
    long fileId = NO_ID;
    try {
      fileId = lock.lock(LockType.FILE);
    }
    catch (LockException e) {
      throw new FileException(this, fileId, e);
    }
    createBackupFile();
    if (!file.exists()) {
      //nothing to backup
      try {
        lock.unlock(LockType.FILE, fileId);
      }
      catch (LockException e) {
        //Don't throw a file exception because the error didn't affect the
        //backup.
        e.printStackTrace();
      }
      return false;
    }
    boolean success = true;
    //File logFile = new File(userDir, processName.toString()
    //    + axisID.getExtension() + ".log");
    //File backupLogFile = new File(file.getAbsolutePath() + '~');
    //don't delete backup file unless the file to be backed up exists
    if (backupFile.exists()) {
      Utilities.debugPrint(backupFile.getAbsolutePath() + " exists, deleting");
      if (!backupFile.delete()) {
        System.err.println("Unable to delete backup log file: "
            + backupFile.getAbsolutePath());
        if (backupFile.exists()) {
          success = false;
          System.err.println(backupFile.getAbsolutePath() + " still exists!");
        }
        else {
          System.err.println(backupFile.getAbsolutePath() + " does not exist!");
        }
      }
    }
    Utilities.debugPrint(file.getAbsolutePath() + " exists");
    if (!file.renameTo(backupFile)) {
      if (file.exists()) {
        System.err.println(file.getAbsolutePath() + " still exists");
        success = false;
      }
      else {
        System.err.println(file.getAbsolutePath() + " does not exist!");
      }
      if (backupFile.exists()) {
        System.err.println(backupFile.getAbsolutePath() + " still exists!");
      }
      else {
        System.err.println(backupFile.getAbsolutePath() + " does not exist");
      }
      System.err.println("Unable to rename log file to: "
          + backupFile.getAbsolutePath());
      StringBuffer message = new StringBuffer("Unable to rename "
          + file.getAbsolutePath() + " to " + backupFile.getAbsolutePath());
      if (Utilities.isWindowsOS()) {
        message
            .append("\nIf either of these files is open in 3dmod, close 3dmod.");
      }
      throw new FileException(this, fileId, message.toString());
    }
    //reset the File variables sinces the file names may have changed.
    file = null;
    backupFile = null;
    try {
      lock.unlock(LockType.FILE, fileId);
    }
    catch (LockException e) {
      e.printStackTrace();
    }
    return success;
  }

  public synchronized boolean create() throws FileException {
    createFile();
    if (file.exists()) {
      return false;
    }
    long fileId = NO_ID;
    try {
      fileId = lock.lock(LockType.FILE);
    }
    catch (LockException e) {
      throw new FileException(this, fileId, e);
    }
    if (file.exists()) {
      //nothing to create
      try {
        lock.unlock(LockType.FILE, fileId);
      }
      catch (LockException e) {
        //Don't throw a file exception because the error didn't affect the
        //create.
        e.printStackTrace();
      }
      return false;
    }
    try {
      file.createNewFile();
    }
    catch (IOException e) {
      throw new FileException(this, e);
    }
    boolean success = file.exists();
    try {
      lock.unlock(LockType.FILE, fileId);
    }
    catch (LockException e) {
      e.printStackTrace();
    }
    if (success) {
      file = null;
      return true;
    }
    String path = file.getAbsolutePath();
    file = null;
    throw new FileException(this, fileId, "Unable to create " + path);
  }

  public synchronized boolean delete() throws FileException {
    createFile();
    if (!file.exists()) {
      return false;
    }
    long fileId = NO_ID;
    try {
      fileId = lock.lock(LockType.FILE);
    }
    catch (LockException e) {
      throw new FileException(this, fileId, e);
    }
    if (!file.exists()) {
      //nothing to delete
      try {
        lock.unlock(LockType.FILE, fileId);
      }
      catch (LockException e) {
        //Don't throw a file exception because the error didn't affect the
        //delete.
        e.printStackTrace();
      }
      return false;
    }
    file.delete();
    try {
      Thread.sleep(500);
    }
    catch (InterruptedException e) {
    }
    boolean success = !file.exists();
    try {
      lock.unlock(LockType.FILE, fileId);
    }
    catch (LockException e) {
      e.printStackTrace();
    }
    if (success) {
      file = null;
      return true;
    }
    String path = file.getAbsolutePath();
    file = null;
    throw new FileException(this, fileId, "Unable to delete " + path);
  }

  public synchronized boolean move(LogFile target) throws FileException {
    createFile();
    if (!file.exists()) {
      return false;
    }
    long fileId = NO_ID;
    try {
      fileId = lock.lock(LockType.FILE);
    }
    catch (LockException e) {
      throw new FileException(this, fileId, e);
    }
    if (!file.exists()) {
      //nothing to move
      try {
        lock.unlock(LockType.FILE, fileId);
      }
      catch (LockException e) {
        //Don't throw a file exception because the error didn't affect the
        //move.
        e.printStackTrace();
      }
      return false;
    }
    try {
    target.backup();
    }
    catch (FileException backupException) {
      //unable to backup
      try {
        lock.unlock(LockType.FILE, fileId);
      }
      catch (LockException e) {
        //Don't throw a file exception because the error didn't affect the
        //move.
        e.printStackTrace();
      }
      
      throw backupException;
    }
    //need to get a file lock on the target for this operation
    long targetFileId = NO_ID;
    try {
      targetFileId = target.lock.lock(LockType.FILE);
    }
    catch (LockException e) {
      throw new FileException(this, targetFileId, e);
    }
    target.createFile();
    file.renameTo(target.file);
    try {
      Thread.sleep(500);
    }
    catch (InterruptedException e) {
    }
    boolean success = !file.exists();
    try {
      lock.unlock(LockType.FILE, fileId);
    }
    catch (LockException e) {
      e.printStackTrace();
    }
    //unlock target file lock
    try {
      target.lock.unlock(LockType.FILE, targetFileId);
    }
    catch (LockException e) {
      e.printStackTrace();
    }
    if (success) {
      file = null;
      return true;
    }
    String path = file.getAbsolutePath();
    file = null;
    throw new FileException(this, fileId, "Unable to delete " + path);
  }

  public synchronized long openWriter() throws WriteException {
    return openForWriting(true);
  }

  /**
   * Run open(long) with no wait limit.  This function can cause deadlock.
   * @see waitForLock()
   * @return
   */
  public synchronized long openForWriting() {
    long writeId = NO_ID;
    try {
      writeId = openForWriting(false);
    }
    catch (WriteException e) {
      e.printStackTrace();
    }
    return writeId;
  }

  private long openForWriting(boolean openWriter) throws WriteException {
    long writeId = NO_ID;
    try {
      writeId = lock.lock(LockType.WRITE);
    }
    catch (LockException e) {
      throw new WriteException(this, e);
    }
    if (openWriter) {
      try {
        createWriter();
      }
      catch (IOException e) {
        try {
          lock.unlock(LockType.WRITE, writeId);
        }
        catch (LockException e0) {
          e0.printStackTrace();
        }
        throw new WriteException(this, e);
      }
    }
    return writeId;
  }

  /**
   * Opens the input stream.  Although this is a reader, it needs to exclude
   * writers because it is used to read the entire file.  So I'm using the
   * writer lock for now.  If more then one input stream must be opened at a
   * time, I will add a new input stream lock that will allow multiple input
   * streams (and readers), but no writers.
   * @return
   * @throws InputStreamException
   */
  public synchronized long openInputStream() throws WriteException {
    long writeId = NO_ID;
    try {
      writeId = lock.lock(LockType.WRITE);
    }
    catch (LockException e) {
      throw new WriteException(this, e);
    }
    try {
      createInputStream();
    }
    catch (IOException e) {
      try {
        lock.unlock(LockType.WRITE, writeId);
      }
      catch (LockException e0) {
        e0.printStackTrace();
      }
      throw new WriteException(this, e);
    }
    return writeId;
  }

  /**
   * Opens the output stream.  Locks the WRITE lock and returns a writeId.
   * @return
   * @throws WriteException
   */
  public synchronized long openOutputStream() throws WriteException {
    long writeId = NO_ID;
    try {
      writeId = lock.lock(LockType.WRITE);
    }
    catch (LockException e) {
      throw new WriteException(this, e);
    }
    try {
      createOutputStream();
    }
    catch (IOException e) {
      try {
        lock.unlock(LockType.WRITE, writeId);
      }
      catch (LockException e0) {
        e0.printStackTrace();
      }
      throw new WriteException(this, e);
    }
    return writeId;
  }

  public synchronized boolean closeInputStream(long writeId) {
    if (fileWriter != null) {
      new WriteException(this, writeId,
          "Must use closeWriter() when opened with openWriter()")
          .printStackTrace();
      return false;
    }
    if (outputStream != null) {
      new WriteException(this, writeId,
          "Must use closeOutputStream() when opened with openOutputStream()")
          .printStackTrace();
      return false;
    }
    if (inputStream == null) {
      new WriteException(this, writeId,
          "Must use closeForWriting() when opened with openForWriting()")
          .printStackTrace();
      return false;
    }
    //close the input stream before unlocking
    try {
      lock.assertUnlockable(LockType.WRITE, writeId);
      closeInputStream();
      lock.unlock(LockType.WRITE, writeId);
    }
    catch (IOException e) {
      e.printStackTrace();
      return false;
    }
    catch (LockException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  public synchronized boolean closeOutputStream(long writeId) {
    if (fileWriter != null) {
      new WriteException(this, writeId,
          "Must use closeWriter() when opened with openWriter()")
          .printStackTrace();
      return false;
    }
    if (inputStream != null) {
      new WriteException(this, writeId,
          "Must use closeInputStream() when opened with openInputStream()")
          .printStackTrace();
      return false;
    }
    if (outputStream == null) {
      new WriteException(this, writeId,
          "Must use closeForWriting() when opened with openForWriting()")
          .printStackTrace();
      return false;
    }
    //close the input stream before unlocking
    try {
      lock.assertUnlockable(LockType.WRITE, writeId);
      closeOutputStream();
      lock.unlock(LockType.WRITE, writeId);
    }
    catch (IOException e) {
      e.printStackTrace();
      return false;
    }
    catch (LockException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  /**
   * Unlocks the open variable and closes the writer
   */
  public synchronized boolean closeForWriting(long writeId) {
    if (fileWriter != null) {
      new WriteException(this, writeId,
          "Must use closeWriter() when opened with openWriter()")
          .printStackTrace();
      return false;
    }
    if (inputStream != null) {
      new WriteException(this, writeId,
          "Must use closeInputStream() when opened with openInputStream()")
          .printStackTrace();
      return false;
    }
    if (outputStream != null) {
      new WriteException(this, writeId,
          "Must use closeOutputStream() when opened with openOutputStream()")
          .printStackTrace();
      return false;
    }
    try {
      lock.unlock(LockType.WRITE, writeId);
    }
    catch (LockException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  public synchronized boolean closeWriter(long writeId) {
    if (inputStream != null) {
      new WriteException(this, writeId,
          "Must use closeInputStream() when opened with openInputStream()")
          .printStackTrace();
      return false;
    }
    if (outputStream != null) {
      new WriteException(this, writeId,
          "Must use closeOutputStream() when opened with openOutputStream()")
          .printStackTrace();
      return false;
    }
    if (fileWriter == null) {
      new WriteException(this, writeId,
          "Must use closeForWriting() when opened with openForWriting()")
          .printStackTrace();
      return false;
    }
    //close the writer before unlocking
    try {
      lock.assertUnlockable(LockType.WRITE, writeId);
      closeWriter();
      lock.unlock(LockType.WRITE, writeId);
    }
    catch (IOException e) {
      e.printStackTrace();
      return false;
    }
    catch (LockException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  public synchronized long openReader() throws ReadException {
    long readId = NO_ID;
    try {
      readId = lock.lock(LockType.READ);
    }
    catch (LockException e) {
      e.printStackTrace();
      throw new ReadException(this, e);
    }
    createFile();
    String idKey = ReaderList.makeKey(file, readId);
    createReaderList();
    try {
      readerList.openReader(idKey, file);
    }
    catch (FileNotFoundException e) {
      try {
        lock.unlock(LockType.READ, readId);
      }
      catch (LockException e0) {
        e0.printStackTrace();
      }
      throw new ReadException(this, readId, e);
    }
    return readId;
  }

  public synchronized boolean closeReader(long readId) {
    //close the reader before unlocking
    try {
      lock.assertUnlockable(LockType.READ, readId);
      createFile();
      createReaderList();
      Reader reader = readerList.get(ReaderList.makeKey(file, readId));
      reader.close();
      lock.unlock(LockType.READ, readId);
    }
    catch (IOException e) {
      e.printStackTrace();
      return false;
    }
    catch (LockException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  public synchronized String readLine(long readId) throws ReadException {
    if (!lock.isLocked(LockType.READ, readId)) {
      throw new ReadException(this, readId);
    }
    createFile();
    createReaderList();
    try {
      return readerList.get(ReaderList.makeKey(file, readId)).readLine();
    }
    catch (IOException e) {
      throw new ReadException(this, readId, e);
    }
  }

  public synchronized void load(Properties properties, long writeId)
      throws WriteException {
    if (inputStream == null || !lock.isLocked(LockType.WRITE, writeId)) {
      throw new WriteException(this, writeId);
    }
    try {
      properties.load(inputStream);
    }
    catch (IOException e) {
      throw new WriteException(this, writeId, e);
    }
  }

  public synchronized void store(Properties properties, long writeId)
      throws WriteException {
    if (outputStream == null || !lock.isLocked(LockType.WRITE, writeId)) {
      throw new WriteException(this, writeId);
    }
    try {
      properties.store(outputStream, null);
    }
    catch (IOException e) {
      throw new WriteException(this, writeId, e);
    }
  }

  public synchronized void write(String string, long writeId)
      throws WriteException {
    if (fileWriter == null || !lock.isLocked(LockType.WRITE, writeId)) {
      throw new WriteException(this, writeId);
    }
    try {
      bufferedWriter.write(string);
    }
    catch (IOException e) {
      throw new WriteException(this, writeId, e);
    }
  }

  public synchronized void newLine(long writeId) throws WriteException {
    if (fileWriter == null || !lock.isLocked(LockType.WRITE, writeId)) {
      throw new WriteException(this, writeId);
    }
    try {
      bufferedWriter.newLine();
    }
    catch (IOException e) {
      throw new WriteException(this, writeId, e);
    }
  }

  public synchronized void flush(long writeId) throws WriteException {
    if (!lock.isLocked(LockType.WRITE, writeId)) {
      throw new WriteException(this, writeId);
    }
    try {
      bufferedWriter.flush();
    }
    catch (IOException e) {
      throw new WriteException(this, writeId, e);
    }
    catch (NullPointerException e) {
      throw new WriteException(this, writeId,
          "Must open with openWriter() to be able to call flush().", e);
    }
  }

  private void createFile() {
    if (file != null) {
      return;
    }
    file = new File(fileAbsolutePath);
  }

  private void createReaderList() {
    if (readerList != null) {
      return;
    }
    readerList = new ReaderList();
  }

  private void createBackupFile() {
    if (backupFile != null) {
      return;
    }
    backupFile = new File(fileAbsolutePath + DatasetFiles.BACKUP_CHAR);
  }

  private void createWriter() throws IOException {
    createFile();
    try {
      if (fileWriter == null) {
        fileWriter = new FileWriter(file);
        bufferedWriter = new BufferedWriter(fileWriter);
      }
    }
    catch (IOException e) {
      try {
        closeWriter();
      }
      catch (IOException e0) {
        e0.printStackTrace();
      }
      throw e;
    }
  }

  private void createInputStream() throws IOException {
    createFile();
    try {
      if (inputStream == null) {
        inputStream = new FileInputStream(file);
      }
    }
    catch (IOException e) {
      try {
        closeInputStream();
      }
      catch (IOException e0) {
        e0.printStackTrace();
      }
      throw e;
    }
  }

  private void createOutputStream() throws IOException {
    createFile();
    try {
      if (outputStream == null) {
        outputStream = new FileOutputStream(file);
      }
    }
    catch (IOException e) {
      try {
        closeOutputStream();
      }
      catch (IOException e0) {
        e0.printStackTrace();
      }
      throw e;
    }
  }

  private void closeWriter() throws IOException {
    if (bufferedWriter != null) {
      bufferedWriter.close();
      bufferedWriter = null;
    }
    if (fileWriter != null) {
      fileWriter.close();
      fileWriter = null;
    }
  }

  private void closeInputStream() throws IOException {
    if (inputStream != null) {
      inputStream.close();
      inputStream = null;
    }
  }

  private void closeOutputStream() throws IOException {
    if (outputStream != null) {
      outputStream.close();
      outputStream = null;
    }
  }

  public synchronized boolean exists() {
    createFile();
    return file.exists();
  }

  public synchronized long lastModified() {
    createFile();
    return file.lastModified();
  }

  public synchronized String getAbsolutePath() {
    createFile();
    return file.getAbsolutePath();
  }

  public synchronized String getName() {
    createFile();
    return file.getName();
  }

  private static String makeKey(File file) {
    return file.getAbsolutePath();
  }

  /**
   * @return true if the open variabled is locked.
   */
  public synchronized boolean isOpen(LockType lockType, long id) {
    return lock.isLocked(lockType, id);
  }

  synchronized boolean noLocks() {
    try {
      lock.assertNoLocks();
    }
    catch (LockException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  static final class LockType {
    static final LockType READ = new LockType("read");
    static final LockType WRITE = new LockType("write");
    static final LockType FILE = new LockType("file");

    private final String name;

    private LockType(String name) {
      this.name = name;
    }

    public String toString() {
      return name;
    }
  }

  public static final class ReadException extends Exception {
    ReadException(LogFile logFile, long id) {
      super("id=" + id + ",logFile=" + logFile + PUBLIC_EXCEPTION_MESSAGE);
    }

    ReadException(LogFile logFile, Exception e) {
      super(e.toString() + "\nlogFile=" + logFile + PUBLIC_EXCEPTION_MESSAGE);
      e.printStackTrace();
    }

    ReadException(LogFile logFile, long id, Exception e) {
      super(e.toString() + "\nid=" + id + ",logFile=" + logFile
          + PUBLIC_EXCEPTION_MESSAGE);
      e.printStackTrace();
    }
  }

  public static class WriteException extends Exception {
    WriteException(LogFile logFile, long id) {
      super("id=" + id + ",logFile=" + logFile + PUBLIC_EXCEPTION_MESSAGE);
    }

    WriteException(LogFile logFile, Exception e) {
      super(e.toString() + "\nlogFile=" + logFile + PUBLIC_EXCEPTION_MESSAGE);
      e.printStackTrace();
    }

    WriteException(LogFile logFile, long id, String message) {
      super(message + "\nid=" + id + ",logFile=" + logFile
          + PUBLIC_EXCEPTION_MESSAGE);
    }

    WriteException(LogFile logFile, long id, Exception e) {
      super(e.toString() + "\nid=" + id + ",logFile=" + logFile
          + PUBLIC_EXCEPTION_MESSAGE);
      e.printStackTrace();
    }

    WriteException(LogFile logFile, long id, String message, Exception e) {
      super(message + '\n' + e.toString() + "\nid=" + id + ",logFile="
          + logFile + PUBLIC_EXCEPTION_MESSAGE);
      e.printStackTrace();
    }
  }

  public static final class FileException extends Exception {
    FileException(LogFile logFile, long id, Exception e) {
      super(e.toString() + "\nid=" + id + ",logFile=" + logFile
          + PUBLIC_EXCEPTION_MESSAGE);
      e.printStackTrace();
    }

    FileException(LogFile logFile, long id, String message) {
      super(message + "\nid=" + id + ",logFile=" + logFile
          + PUBLIC_EXCEPTION_MESSAGE);
    }

    FileException(LogFile logFile, Exception e) {
      super(e.toString() + "\nlogFile=" + logFile + PUBLIC_EXCEPTION_MESSAGE);
      e.printStackTrace();
    }
  }

  private static final class LockException extends Exception {
    LockException(Lock lock) {
      super("lock=" + lock);
    }

    LockException(Lock lock, LockType lockType) {
      super("lockType=" + lockType + ",lock=" + lock);
    }

    LockException(Lock lock, LockType lockType, long id) {
      super("lockType=" + lockType + ",id=" + id + ",lock=" + lock);
    }
  }

  private static final class Lock {
    private boolean locked = false;

    private long currentId = NO_ID;
    private HashMap readIdHashMap = null;
    private long writeId = NO_ID;
    private long fileId = NO_ID;

    private static String makeKey(long id) {
      return String.valueOf(id);
    }

    public String toString() {
      return "\n[readIdHashMap=" + readIdHashMap + ",\nwrite=Id=" + writeId
          + ",fileId=" + fileId + "]";
    }

    long lock(LockType lockType) throws LockException {
      assertLockable(lockType);
      //set the lock
      locked = true;
      //save the lock id in the variable matching the lock type
      //increment the current id
      if (++currentId < 0) {
        System.err
            .println("LogFile overflow - setting currentId to zero:currentId="
                + currentId);
        //catching overflow
        currentId = 0;
      }
      if (lockType == LockType.READ) {
        createReadIdHashMap();
        readIdHashMap.put(makeKey(currentId), null);
      }
      else if (lockType == LockType.WRITE) {
        writeId = currentId;
      }
      else {
        fileId = currentId;
      }
      return currentId;
    }

    void unlock(LockType lockType, long id) throws LockException {
      assertUnlockable(lockType, id);
      createReadIdHashMap();
      //unsetting the matching saved id
      String readKey = makeKey(id);
      if (lockType == LockType.READ && readIdHashMap.containsKey(readKey)) {
        readIdHashMap.remove(readKey);
      }
      else if (lockType == LockType.WRITE && id == writeId) {
        writeId = NO_ID;
      }
      else if (lockType == LockType.FILE && id == fileId) {
        fileId = NO_ID;
      }
      else {
        throw new LockException(this, lockType);
      }
      //turn off locked if all the saved ids are empty
      if (readIdHashMap.isEmpty() && writeId == NO_ID && fileId == NO_ID) {
        locked = false;
      }
      return;
    }

    boolean isLocked(LockType lockType, long id) {
      if (!locked || lockType == null || id == NO_ID) {
        return false;
      }
      createReadIdHashMap();
      return (lockType == LockType.READ && readIdHashMap
          .containsKey(makeKey(id)))
          || (lockType == LockType.WRITE && id == writeId)
          || (lockType == LockType.FILE && id == fileId);
    }

    boolean isLocked(LockType lockType) {
      if (!locked || lockType == null) {
        return false;
      }
      createReadIdHashMap();
      return (lockType == LockType.READ && !readIdHashMap.isEmpty())
          || (lockType == LockType.WRITE && writeId != NO_ID)
          || (lockType == LockType.FILE && fileId != NO_ID);
    }

    void assertNoLocks() throws LockException {
      if (locked) {
        throw new LockException(this);
      }
    }

    private void assertLockable(LockType lockType) throws LockException {
      if (!locked || lockType == null) {
        //succeed - not locked
        return;
      }
      //nothing else can be done during a file lock
      //only one write can be done at a time
      if (lockType == LockType.FILE || fileId != NO_ID
          || (lockType == LockType.WRITE && writeId != NO_ID)) {
        throw new LockException(this, lockType);
      }
      //compatible:
      //multiple reads
      //a read and write (either can be started first)
    }

    void assertUnlockable(LockType lockType, long id) throws LockException {
      if (!locked) {
        throw new LockException(this, lockType, id);
      }
      createReadIdHashMap();
      if (readIdHashMap.isEmpty() && writeId == NO_ID && fileId == NO_ID) {
        throw new IllegalStateException(
            "Ids don't match the locked boolean:\nlocked=" + locked
                + ",readId=" + readIdHashMap.toString() + ",writeId=" + writeId
                + ",fileId=" + fileId);
      }
      //checking for unlockability
      if ((lockType == LockType.READ && readIdHashMap.containsKey(makeKey(id)))
          || (lockType == LockType.WRITE && id == writeId)
          || (lockType == LockType.FILE && id == fileId)) {
        return;
      }
      throw new LockException(this, lockType, id);
    }

    long getWriteId() {
      return writeId;
    }

    private void createReadIdHashMap() {
      if (readIdHashMap == null) {
        readIdHashMap = new HashMap();
      }
    }
  }

  private static final class ReaderList {
    private final HashMap hashMap;
    private final ArrayList arrayList;

    ReaderList() {
      hashMap = new HashMap();
      arrayList = new ArrayList();
    }

    static String makeKey(File file, long id) {
      return file.getAbsolutePath() + String.valueOf(id);
    }

    synchronized Reader get(String key) {
      return (Reader) hashMap.get(key);
    }

    synchronized void openReader(String currentKey, File file)
        throws FileNotFoundException {
      Reader reader;
      for (int i = 0; i < arrayList.size(); i++) {
        reader = (Reader) arrayList.get(i);
        if (!reader.isOpen()) {
          //open the reader to get exclusive access to it
          try {
            reader.open();
          }
          catch (FileNotFoundException e) {
            throw new FileNotFoundException(e.getMessage() + "\ncurrentKey="
                + currentKey);
          }
          //Found a closed reader, so reuse it
          //get the old key from the reader and rekey the reader in the hash map
          //with the current key
          String oldKey = reader.getKey();
          hashMap.remove(oldKey);
          reader.setKey(currentKey);
          hashMap.put(currentKey, reader);

        }
      }
      //Can't find a closed reader, so create a new one
      reader = new Reader(file);
      //open the reader to get exclusive access to it
      reader.open();
      //store the current key in the reader and store it in the array list and
      //hash map
      reader.setKey(currentKey);
      hashMap.put(currentKey, reader);
      arrayList.add(reader);
    }

    synchronized Reader getReader(String key) {
      return (Reader) hashMap.get(key);
    }
  }

  private static final class Reader {
    private boolean open = true;

    private final File file;

    private FileReader fileReader = null;
    private BufferedReader bufferedReader = null;
    private String key = null;

    Reader(File file) {
      this.file = file;
    }

    void open() throws FileNotFoundException {
      if (fileReader == null) {
        fileReader = new FileReader(file.getAbsolutePath());
      }
      if (bufferedReader == null) {
        bufferedReader = new BufferedReader(fileReader);
      }
      open = true;
    }

    void close() throws IOException {
      if (fileReader != null) {
        fileReader.close();
      }
      if (bufferedReader != null) {
        bufferedReader.close();
      }
      open = false;
    }

    String readLine() throws IOException {
      return bufferedReader.readLine();
    }

    boolean isOpen() {
      return open;
    }

    void setKey(String key) {
      this.key = key;
    }

    String getKey() {
      return key;
    }
  }
}
/**
 * <p> $Log$
 * <p> Revision 1.8  2006/11/15 20:03:13  sueh
 * <p> bug# 872 Added backupOnce, input stream and output stream management,
 * <p> create, load(), and store().
 * <p>
 * <p> Revision 1.7  2006/10/16 22:46:54  sueh
 * <p> bug# 919  Reader.open():  Simplifying new FileReader error handling.
 * <p>
 * <p> Revision 1.6  2006/10/13 22:29:48  sueh
 * <p> bug# 931 Making LockType package level, since its no used outside of LogFile.
 * <p>
 * <p> Revision 1.5  2006/10/12 10:41:16  sueh
 * <p> bug# 931 Sleeping longer in delete because windows is slow.
 * <p>
 * <p> Revision 1.4  2006/10/12 03:19:56  sueh
 * <p> bug# 931 In newLine() an write() throw WriteException instead of
 * <p> NullPointerException when the writer is not open.
 * <p>
 * <p> Revision 1.3  2006/10/11 10:12:05  sueh
 * <p> bug# 931 Added delete functionality to LogFile - changed BackupException to
 * <p> FileException.
 * <p>
 * <p> Revision 1.2  2006/10/10 07:44:20  sueh
 * <p> bug# 931 When BufferedWriter.close() is called, the instance can't be
 * <p> reopened, so don't preserve the buffered writer instance.
 * <p>
 * <p> Revision 1.1  2006/10/10 05:18:57  sueh
 * <p> Bug# 931 Class to manage log files.  Prevents access that would violate  Windows file locking.  Handles backups, reading, and writing.  Also can use to
 * <p> prevent access while another process is writing to the file.
 * <p> </p>
 */
