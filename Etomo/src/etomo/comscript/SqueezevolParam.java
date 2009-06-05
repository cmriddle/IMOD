package etomo.comscript;

import java.io.File;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Properties;

import etomo.ApplicationManager;
import etomo.BaseManager;
import etomo.type.AxisID;
import etomo.type.ConstEtomoNumber;
import etomo.type.ConstIntKeyList;
import etomo.type.EtomoNumber;
import etomo.type.FileType;

/**
 * <p>Description: </p>
 * 
 * <p>Copyright: Copyright (c) 2002 - 2006</p>
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
 * <p> Revision 1.7  2007/11/06 19:17:07  sueh
 * <p> bug# 1047 Added getSubcommandDetails.
 * <p>
 * <p> Revision 1.6  2006/01/20 20:48:06  sueh
 * <p> updated copyright year
 * <p>
 * <p> Revision 1.5  2005/07/29 00:49:55  sueh
 * <p> bug# 709 Going to EtomoDirector to get the current manager is unreliable
 * <p> because the current manager changes when the user changes the tab.
 * <p> Passing the manager where its needed.
 * <p>
 * <p> Revision 1.4  2004/12/16 02:12:44  sueh
 * <p> bug# 564 Saved flipped status.
 * <p>
 * <p> Revision 1.3  2004/12/14 21:32:55  sueh
 * <p> bug# 557 Made separate variables for x and y reduction factors to handle
 * <p> an unflipped tomogram.
 * <p>
 * <p> Revision 1.2  2004/12/02 18:26:02  sueh
 * <p> bug# 557 Moved everything except public functions that change
 * <p> parameters the ConstSqueezevolParam.  Added load().
 * <p>
 * <p> Revision 1.1  2004/12/01 03:46:14  sueh
 * <p> bug# 557 Parameter for squeezevol.
 * <p> </p>
 */
public final class SqueezevolParam implements ConstSqueezevolParam
    {
  public static final String rcsid = "$Id$";

  private static final String GROUP_STRING = "Squeezevol";
  private static final String LINEAR_INTERPOLATION_STRING = "LinearInterpolation";
  private static final boolean DEFAULT_LINEAR_INTERPOLATION = false;
  private static final int COMMAND_SIZE = 3;
  private static final String COMMAND_NAME = "squeezevol";

  private final EtomoNumber reductionFactorX = new EtomoNumber(
      EtomoNumber.Type.DOUBLE, "ReductionFactorX");
  private final EtomoNumber reductionFactorY = new EtomoNumber(
      EtomoNumber.Type.DOUBLE, "ReductionFactorY");
  private final EtomoNumber reductionFactorZ = new EtomoNumber(
      EtomoNumber.Type.DOUBLE, "ReductionFactorZ");

  private final ApplicationManager manager;

  boolean linearInterpolation;
  private String[] commandArray = null;
  private File outputFile;
  String inputFile = null;
  boolean flipped = false;

  public SqueezevolParam(final ApplicationManager manager) {
    this.manager = manager;
    reductionFactorX.setDisplayValue(1.25);
    reductionFactorY.setDisplayValue(1.25);
    reductionFactorZ.setDisplayValue(1.25);
    reset();
  }

  /**
   *  Get the objects attributes from the properties object.
   */
  public void load(final Properties props) {
    load(props, "");
  }

  public void load(final Properties props, String prepend) {
    reset();
    prepend = createPrepend(prepend);
    String group = prepend + ".";

    reductionFactorX.load(props, prepend);
    reductionFactorY.load(props, prepend);
    reductionFactorZ.load(props, prepend);
    linearInterpolation = Boolean.valueOf(
        props.getProperty(group + LINEAR_INTERPOLATION_STRING, Boolean
            .toString(DEFAULT_LINEAR_INTERPOLATION))).booleanValue();
  }

  public ConstEtomoNumber setReductionFactorX(final String reductionFactorX) {
    return this.reductionFactorX.set(reductionFactorX);
  }

  public CommandDetails getSubcommandDetails() {
    return null;
  }

  public void setReductionFactorY(final String reductionFactorY) {
    this.reductionFactorY.set(reductionFactorY);
  }

  public void setReductionFactorZ(final String reductionFactorZ) {
    this.reductionFactorZ.set(reductionFactorZ);
  }

  public void setLinearInterpolation(final boolean linearInterpolation) {
    this.linearInterpolation = linearInterpolation;
  }

  public boolean setFlipped(final boolean flipped) {
    return this.flipped = flipped;
  }

  public void setInputFile(FileType fileType) {
    inputFile = fileType.getFileName(manager);
  }

  public AxisID getAxisID() {
    return AxisID.ONLY;
  }

  private void reset() {
    reductionFactorX.reset();
    reductionFactorY.reset();
    reductionFactorZ.reset();
    linearInterpolation = DEFAULT_LINEAR_INTERPOLATION;
  }

  private ArrayList genOptions() {
    ArrayList options = new ArrayList();
    options.add("-x");
    options.add(reductionFactorX.toString());
    options.add("-y");
    options.add(reductionFactorY.toString());
    options.add("-z");
    options.add(reductionFactorZ.toString());
    if (linearInterpolation) {
      options.add("-l");
    }
    options.add("-P");
    options.add(inputFile);
    //output is dataset.sqz
    outputFile = FileType.SQUEEZE_VOL_OUTPUT.getFile(manager);
    options.add(outputFile.getName());
    return options;
  }

  private String getInputFileName(final String datasetName) {
    return TrimvolParam.getOutputFileName(datasetName);
  }

  public void store(final Properties props) {
    store(props, "");
  }

  public void store(final Properties props, String prepend) {
    prepend = createPrepend(prepend);
    String group = prepend + ".";

    reductionFactorX.store(props, prepend);
    reductionFactorY.store(props, prepend);
    reductionFactorZ.store(props, prepend);
    props.setProperty(group + LINEAR_INTERPOLATION_STRING, Boolean
        .toString(linearInterpolation));
  }

  private static String createPrepend(final String prepend) {
    if (prepend == "") {
      return GROUP_STRING;
    }
    return prepend + "." + GROUP_STRING;
  }

  private void createCommand() {
    ArrayList options = genOptions();
    commandArray = new String[options.size() + COMMAND_SIZE];
    commandArray[0] = "tcsh";
    commandArray[1] = "-f";
    commandArray[2] = BaseManager.getIMODBinPath() + COMMAND_NAME;
    for (int i = 0; i < options.size(); i++) {
      commandArray[i + COMMAND_SIZE] = (String) options.get(i);
    }
  }

  /**
   * Get command array used to run command.  Not for running the command
   * @return
   */
  public String getCommandLine() {
    if (commandArray == null) {
      return "";
    }
    StringBuffer buffer = new StringBuffer();
    for (int i = 0; i < commandArray.length; i++) {
      buffer.append(commandArray[i] + " ");
    }
    return buffer.toString();
  }

  /**
   * Get command array to run
   * @return
   */
  public String[] getCommandArray() {
    createCommand();
    return commandArray;
  }

  public boolean getBooleanValue(etomo.comscript.Field field) {
    if (field == Fields.FLIPPED) {
      return flipped;
    }
    throw new IllegalArgumentException("field=" + field);
  }

  public float getFloatValue(etomo.comscript.Field field) {
    throw new IllegalArgumentException("field=" + field);
  }

  public String[] getStringArray(etomo.comscript.Field field) {
    throw new IllegalArgumentException("field=" + field);
  }

  public String getString(etomo.comscript.Field field) {
    throw new IllegalArgumentException("field=" + field);
  }

  public Hashtable getHashtable(etomo.comscript.Field field) {
    throw new IllegalArgumentException("field=" + field);
  }

  public int getIntValue(etomo.comscript.Field field) {
    throw new IllegalArgumentException("field=" + field);
  }

  public double getDoubleValue(etomo.comscript.Field field) {
    throw new IllegalArgumentException("field=" + field);
  }

  public ConstEtomoNumber getEtomoNumber(etomo.comscript.Field field) {
    throw new IllegalArgumentException("field=" + field);
  }

  public ConstIntKeyList getIntKeyList(etomo.comscript.Field field) {
    throw new IllegalArgumentException("field=" + field);
  }

  public String getCommandName() {
    return COMMAND_NAME;
  }

  public String getCommand() {
    return COMMAND_NAME;
  }

  public static String getName() {
    return COMMAND_NAME;
  }

  public CommandMode getCommandMode() {
    return null;
  }

  public File getCommandOutputFile() {
    return outputFile;
  }

  public ConstEtomoNumber getReductionFactorX() {
    return reductionFactorX;
  }

  public ConstEtomoNumber getReductionFactorY() {
    return reductionFactorY;
  }

  public ConstEtomoNumber getReductionFactorZ() {
    return reductionFactorZ;
  }

  public boolean isLinearInterpolation() {
    return linearInterpolation;
  }

  public boolean isFlipped() {
    return flipped;
  }

  public boolean equals(final ConstSqueezevolParam param) {
    if (!reductionFactorX.equals(param.getReductionFactorX())) {
      return false;
    }
    if (!reductionFactorY.equals(param.getReductionFactorY())) {
      return false;
    }
    if (!reductionFactorZ.equals(param.getReductionFactorZ())) {
      return false;
    }
    if (linearInterpolation != param.isLinearInterpolation()) {
      return false;
    }
    return true;
  }

  public static final class Fields implements etomo.comscript.Field {
    private Fields() {
    }

    public static final Fields FLIPPED = new Fields();
  }
}
