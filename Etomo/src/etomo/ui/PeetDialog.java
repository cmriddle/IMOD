package etomo.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.SpinnerNumberModel;

import etomo.PeetManager;
import etomo.comscript.ParallelParam;
import etomo.comscript.ProcesschunksParam;
import etomo.storage.LogFile;
import etomo.storage.MatlabParamFile;
import etomo.storage.autodoc.AutodocFactory;
import etomo.storage.autodoc.ReadOnlyAutodoc;
import etomo.type.AxisID;
import etomo.type.ConstPeetMetaData;
import etomo.type.ConstPeetScreenState;
import etomo.type.DialogType;
import etomo.type.EtomoAutodoc;
import etomo.type.PeetMetaData;
import etomo.type.PeetScreenState;

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
 * <p> Revision 1.24  2007/04/13 21:52:33  sueh
 * <p> bug# 964 Saving/retrieving debugLevel to/from MatlabParamFile.
 * <p>
 * <p> Revision 1.23  2007/04/13 18:50:24  sueh
 * <p> bug# 964 Saving/retrieving ccMode, meanFill, and lowCutoff to/from
 * <p> MatlabParamFile.  Adding EnumerationTypes directly to associated radio buttons.
 * <p>
 * <p> Revision 1.22  2007/04/11 22:22:06  sueh
 * <p> bug# 964 Saving edgeShift to meta data and MatlabParamFile.
 * <p>
 * <p> Revision 1.21  2007/04/09 22:00:21  sueh
 * <p> bug# 964 Getting and setting szVol from MatlabParamFile.  Filling in Y and Z from
 * <p> X when they are empty.
 * <p>
 * <p> Revision 1.20  2007/04/09 21:20:23  sueh
 * <p> bug# 964 Fixed the names of the reference fields.
 * <p>
 * <p> Revision 1.18  2007/04/02 21:52:40  sueh
 * <p> bug# 964 Rearranged fields.
 * <p>
 * <p> Revision 1.17  2007/04/02 16:03:29  sueh
 * <p> bug# 964 Added Run panel.
 * <p>
 * <p> Revision 1.16  2007/03/31 03:02:40  sueh
 * <p> bug# 964 Added szVol, edgeShift, meanFill, CCMode, and debugLevel.  Setting
 * <p> defaults.  Fixed enable bug.
 * <p>
 * <p> Revision 1.15  2007/03/30 23:51:43  sueh
 * <p> bug# 964 Added fields for the reference parameter.
 * <p>
 * <p> Revision 1.14  2007/03/27 19:31:26  sueh
 * <p> bug# 964
 * <p>
 * <p> Revision 1.13  2007/03/27 00:04:39  sueh
 * <p> bug# 964 Added setTooltipText.
 * <p>
 * <p> Revision 1.12  2007/03/26 18:39:44  sueh
 * <p> bug# 964 Moved InitMOTL and tilt range options to the Run Parameters windows.
 * <p>
 * <p> Revision 1.11  2007/03/23 20:43:03  sueh
 * <p> bug# 964 Fixed getParameters(MatlabParamFile):  tiltRangeEmpty was being
 * <p> set incorrectly.
 * <p>
 * <p> Revision 1.10  2007/03/21 19:46:16  sueh
 * <p> bug# 964 Limiting access to autodoc classes by using ReadOnly interfaces.
 * <p> Added AutodocFactory to create Autodoc instances.
 * <p>
 * <p> Revision 1.9  2007/03/20 23:11:00  sueh
 * <p> bug# 964 Added "Use tilt range" checkbox.
 * <p>
 * <p> Revision 1.8  2007/03/20 00:45:17  sueh
 * <p> bug# 964 Added Initial MOTL radio buttons.
 * <p>
 * <p> Revision 1.7  2007/03/15 21:48:09  sueh
 * <p> bug# 964 Added setParameters(MatlabParamFile).
 * <p>
 * <p> Revision 1.6  2007/03/01 01:41:46  sueh
 * <p> bug# 964 Added initialize() to sets metadata fields that are only set once.
 * <p>
 * <p> Revision 1.5  2007/02/22 20:38:40  sueh
 * <p> bug# 964 Added a button to the Directory field.
 * <p>
 * <p> Revision 1.4  2007/02/21 22:30:22  sueh
 * <p> bug# 964 Fixing null pointer exception which occurred when loading the .epe file.
 * <p>
 * <p> Revision 1.3  2007/02/21 04:24:32  sueh
 * <p> bug# 964 Setting Output and Directory when Save As is called.  Disabling edit
 * <p> for Output and Directory when the paramFile is set.
 * <p>
 * <p> Revision 1.2  2007/02/20 20:36:46  sueh
 * <p> bug# 964 Started the setup panel.
 * <p>
 * <p> Revision 1.1  2007/02/19 22:03:19  sueh
 * <p> bug# 964 Dialog for PEET interface.
 * <p> </p>
 */

public final class PeetDialog implements AbstractParallelDialog, Expandable {
  public static final String rcsid = "$Id$";

  static final String DIRECTORY_LABEL = "Directory";
  static final String OUTPUT_LABEL = "Root name for output";

  private static final String REFERENCE_PARTICLE_LABEL = "Particle #: ";
  private static final String REFERENCE_VOLUME_LABEL = "Volume #: ";
  private static final String REFERENCE_FILE_LABEL = "Reference file: ";

  private static final DialogType DIALOG_TYPE = DialogType.PEET;

  private final JPanel rootPanel = new JPanel();
  private final FileTextField ftfDirectory = new FileTextField(DIRECTORY_LABEL
      + ": ");
  private final LabeledTextField ltfFnOutput = new LabeledTextField(
      OUTPUT_LABEL + ": ");
  private final SpacedPanel pnlSetupBody = new SpacedPanel();
  private final CheckBox cbTiltRange = new CheckBox(
      "Use tilt range for missing wedge compensation");
  private final SpacedPanel pnlRunParametersBody = new SpacedPanel();
  private final LabeledTextField ltfReferenceParticle = new LabeledTextField(
      REFERENCE_PARTICLE_LABEL);
  private final FileTextField ftfReferenceFile = FileTextField
      .getUnlabeledInstance(REFERENCE_FILE_LABEL);
  private final LabeledTextField ltfSzVolX = new LabeledTextField(
      "Particle volume X: ");
  private final LabeledTextField ltfSzVolY = new LabeledTextField("Y: ");
  private final LabeledTextField ltfSzVolZ = new LabeledTextField("Z: ");
  private final LabeledTextField ltfEdgeShift = new LabeledTextField(
      "Edge shift: ");
  private final CheckBox cbMeanFill = new CheckBox("Mean fill");
  private final LabeledTextField ltfAlignedBaseName = new LabeledTextField(
      "Aligned base name: ");
  private final LabeledTextField ltfLowCutoff = new LabeledTextField(
      "Low cutoff: ");
  private final CheckBox cbRefFlagAllTom = new CheckBox(
      "Use equal numbers of particles from all tomogram for new reference");
  private final LabeledTextField ltfLstThresholdsStart = new LabeledTextField(
      "Start: ");
  private final LabeledTextField ltfLstThresholdsIncrement = new LabeledTextField(
      "Incr.: ");
  private final LabeledTextField ltfLstThresholdsEnd = new LabeledTextField(
      "End: ");
  private final LabeledTextField ltfLstThresholdsAdditional = new LabeledTextField(
      " Additional numbers: ");
  private final CheckBox cbLstFlagAllTom = new CheckBox(
      "Use equal numbers of particles from all tomograms for averages");
  private final SpacedPanel pnlRunBody = new SpacedPanel(true);
  private final MultiLineButton btnRun = new MultiLineButton("Run");
  private final JPanel pnlAdvanced = new JPanel();
  private final ButtonGroup bgReference = new ButtonGroup();
  private final RadioButton rbReferenceVolume = new RadioButton(
      REFERENCE_VOLUME_LABEL, bgReference);
  private final Spinner sReferenceVolume = new Spinner(REFERENCE_VOLUME_LABEL
      + ": ");
  private final RadioButton rbReferenceFile = new RadioButton(
      REFERENCE_FILE_LABEL, bgReference);
  private final LabeledSpinner lsParticlePerCPU;
  private final IterationTable iterationTable;
  private final ButtonGroup bgInitMotl = new ButtonGroup();
  private final RadioButton rbInitMotlZero = new RadioButton(
      "Set all rotational values to zero", MatlabParamFile.InitMotlCode.ZERO,
      bgInitMotl);
  private final RadioButton rbInitMotlZAxis = new RadioButton(
      "Initialize Z axis", MatlabParamFile.InitMotlCode.Z_AXIS, bgInitMotl);
  private final RadioButton rbInitMotlXAndZAxis = new RadioButton(
      "Initialize X and Z axis", MatlabParamFile.InitMotlCode.X_AND_Z_AXIS,
      bgInitMotl);
  private final RadioButton rbInitMotlFiles = new RadioButton("Use files",
      bgInitMotl);
  private final PanelHeader phSetup;
  private final PanelHeader phRunParameters;
  private final VolumeTable volumeTable;
  private final PeetManager manager;
  private final AxisID axisID;
  private final ButtonGroup bgCcMode = new ButtonGroup();
  private final RadioButton rbCcModeNormalized = new RadioButton(
      "Local energy normalized cross correlation",
      MatlabParamFile.CCMode.NORMALIZED, bgCcMode);
  private final RadioButton rbCcModeLocal = new RadioButton(
      "True local correlation coefficent", MatlabParamFile.CCMode.LOCAL,
      bgCcMode);
  private final LabeledSpinner lsDebugLevel = new LabeledSpinner(
      "Debug level: ", new SpinnerNumberModel(
          MatlabParamFile.DEBUG_LEVEL_DEFAULT, MatlabParamFile.DEBUG_LEVEL_MIN,
          MatlabParamFile.DEBUG_LEVEL_MAX, 1));
  private final PanelHeader phRun;

  private PeetDialog(final PeetManager manager, final AxisID axisID) {
    this.manager = manager;
    this.axisID = axisID;
    //setup construction
    phSetup = PanelHeader.getInstance("Setup", this, DIALOG_TYPE);
    volumeTable = VolumeTable.getInstance(manager, this);
    //run parameters construction
    phRunParameters = PanelHeader.getAdvancedBasicInstance("Run Parameters",
        this, DIALOG_TYPE);
    //run construction
    phRun = PanelHeader.getInstance("Run", this, DIALOG_TYPE);
    iterationTable = IterationTable.getInstance(manager);
    lsParticlePerCPU = new LabeledSpinner("Particles per CPU: ",
        new SpinnerNumberModel(MatlabParamFile.PARTICLE_PER_CPU_DEFAULT,
            MatlabParamFile.PARTICLE_PER_CPU_MIN,
            MatlabParamFile.PARTICLE_PER_CPU_MAX, 1));
    //panels
    rootPanel.setLayout(new BoxLayout(rootPanel, BoxLayout.Y_AXIS));
    rootPanel.setBorder(new EtchedBorder("PEET").getBorder());
    rootPanel.add(createSetupPanel());
    rootPanel.add(createRunParametersPanel());
    rootPanel.add(createRunPanel());
    setDefaults();
    updateDisplay();
    updateAdvanceRunParameters(phRunParameters.isAdvanced());
    setTooltipText();
  }

  public static PeetDialog getInstance(final PeetManager manager,
      final AxisID axisID) {
    PeetDialog instance = new PeetDialog(manager, axisID);
    instance.addListeners();
    return instance;
  }

  public void updateDisplay(final boolean paramFileSet) {
    ftfDirectory.setEditable(!paramFileSet);
    ltfFnOutput.setEditable(!paramFileSet);
  }

  public Container getContainer() {
    return rootPanel;
  }

  public DialogType getDialogType() {
    return DIALOG_TYPE;
  }

  public void getParameters(final ParallelParam param) {
    ProcesschunksParam processchunksParam = (ProcesschunksParam) param;
    processchunksParam.setRootName(ltfFnOutput.getText());
  }

  public void getParameters(final PeetScreenState screenState) {
    phSetup.getState(screenState.getPeetSetupHeaderState());
    phRunParameters.getState(screenState.getPeetRunParametersHeaderState());
    phRun.getState(screenState.getPeetRunHeaderState());
  }

  public void setParameters(final ConstPeetScreenState screenState) {
    phSetup.setState(screenState.getPeetSetupHeaderState());
    phRunParameters.setState(screenState.getPeetRunParametersHeaderState());
    phRun.setState(screenState.getPeetRunHeaderState());
  }

  public void getParameters(final PeetMetaData metaData) {
    volumeTable.getParameters(metaData);
    metaData.setReferenceVolume(sReferenceVolume.getValue());
    metaData.setReferenceParticle(ltfReferenceParticle.getText());
    metaData.setReferenceFile(ftfReferenceFile.getText());
    metaData.setEdgeShift(ltfEdgeShift.getText());
  }

  /**
   * Set parameters from metaData and then overwrite them with parameters from
   * MatlabParamFile.  This allows inactive data to appear on the screen but
   * allows MatlabParamFile's active data to override active metaData.  So if
   * the user changes the .prm file, the active data on the screen will be
   * correct.
   * @param metaData
   */
  public void setParameters(final ConstPeetMetaData metaData) {
    ltfFnOutput.setText(metaData.getName());
    volumeTable.setParameters(metaData);
    rbReferenceFile.setSelected(true);
    ftfReferenceFile.setText(metaData.getReferenceFile());
    rbReferenceVolume.setSelected(true);
    sReferenceVolume.setValue(metaData.getReferenceVolume());
    ltfReferenceParticle.setText(metaData.getReferenceParticle());
    ltfEdgeShift.setText(metaData.getEdgeShift());
  }

  /**
   * Load data from MatlabParamFile.  Load only active data after the meta data
   * has been loaded.  Do not load fnOutput.  This value cannot be modified after
   * the dataset has been created.
   * @param matlabParamFile
   */
  public void setParameters(final MatlabParamFile matlabParamFile) {
    volumeTable.setParameters(matlabParamFile, rbInitMotlFiles.isSelected(),
        cbTiltRange.isSelected());
    iterationTable.setParameters(matlabParamFile);
    if (matlabParamFile.useReferenceFile()) {
      rbReferenceFile.setSelected(true);
      ftfReferenceFile.setText(matlabParamFile.getReferenceFile());
    }
    else {
      rbReferenceVolume.setSelected(true);
      sReferenceVolume.setValue(matlabParamFile.getReferenceVolume());
      ltfReferenceParticle.setText(matlabParamFile.getReferenceParticle());
    }
    MatlabParamFile.InitMotlCode initMotlCode = matlabParamFile
        .getInitMotlCode();
    if (initMotlCode == null) {
      rbInitMotlFiles.setSelected(true);
    }
    else if (initMotlCode == MatlabParamFile.InitMotlCode.ZERO) {
      rbInitMotlZero.setSelected(true);
    }
    else if (initMotlCode == MatlabParamFile.InitMotlCode.Z_AXIS) {
      rbInitMotlZAxis.setSelected(true);
    }
    else if (initMotlCode == MatlabParamFile.InitMotlCode.X_AND_Z_AXIS) {
      rbInitMotlXAndZAxis.setSelected(true);
    }
    cbTiltRange.setSelected(matlabParamFile.useTiltRange());
    if (cbTiltRange.isSelected()) {
      ltfEdgeShift.setText(matlabParamFile.getEdgeShift());
    }
    ltfSzVolX.setText(matlabParamFile.getSzVolX());
    ltfSzVolY.setText(matlabParamFile.getSzVolY());
    ltfSzVolZ.setText(matlabParamFile.getSzVolZ());
    MatlabParamFile.CCMode ccMode = matlabParamFile.getCcMode();
    if (ccMode == MatlabParamFile.CCMode.NORMALIZED) {
      rbCcModeNormalized.setSelected(true);
    }
    else if (ccMode == MatlabParamFile.CCMode.LOCAL) {
      rbCcModeLocal.setSelected(true);
    }
    cbMeanFill.setSelected(matlabParamFile.isMeanFill());
    ltfAlignedBaseName.setText(matlabParamFile.getAlignedBaseName());
    ltfLowCutoff.setText(matlabParamFile.getLowCutoff());
    lsDebugLevel.setValue(matlabParamFile.getDebugLevel());
    ltfLstThresholdsStart.setText(matlabParamFile.getLstThresholdsStart());
    ltfLstThresholdsIncrement.setText(matlabParamFile.getLstThresholdsIncrement());
    ltfLstThresholdsEnd.setText(matlabParamFile.getLstThresholdsEnd());
    ltfLstThresholdsAdditional.setText(matlabParamFile.getLstThresholdsAdditional());
    updateDisplay();
  }

  public void getParameters(final MatlabParamFile matlabParamFile) {
    matlabParamFile.clear();
    volumeTable.getParameters(matlabParamFile);
    iterationTable.getParameters(matlabParamFile);
    matlabParamFile.setFnOutput(ltfFnOutput.getText());
    if (rbReferenceVolume.isSelected()) {
      matlabParamFile.setReferenceVolume(sReferenceVolume.getValue());
      matlabParamFile.setReferenceParticle(ltfReferenceParticle.getText());
    }
    else if (rbReferenceFile.isSelected()) {
      matlabParamFile.setReferenceFile(ftfReferenceFile.getText());
    }
    matlabParamFile.setInitMotlCode(((RadioButton.RadioButtonModel) bgInitMotl
        .getSelection()).getEnumeratedType());
    matlabParamFile.setTiltRangeEmpty(!cbTiltRange.isSelected());
    if (ltfEdgeShift.isEnabled()) {
      matlabParamFile.setEdgeShift(ltfEdgeShift.getText());
    }
    matlabParamFile.setSzVolX(ltfSzVolX.getText());
    matlabParamFile.setSzVolY(ltfSzVolY.getText());
    matlabParamFile.setSzVolZ(ltfSzVolZ.getText());
    matlabParamFile.setCcMode(((RadioButton.RadioButtonModel) bgCcMode
        .getSelection()).getEnumeratedType());
    matlabParamFile.setMeanFill(cbMeanFill.isSelected());
    matlabParamFile.setAlignedBaseName(ltfAlignedBaseName.getText());
    matlabParamFile.setLowCutoff(ltfLowCutoff.getText());
    matlabParamFile.setDebugLevel(lsDebugLevel.getValue());
    matlabParamFile.setLstThresholdsStart(ltfLstThresholdsStart.getText());
    matlabParamFile.setLstThresholdsIncrement(ltfLstThresholdsIncrement.getText());
    matlabParamFile.setLstThresholdsEnd(ltfLstThresholdsEnd.getText());
    matlabParamFile.setLstThresholdsAdditional(ltfLstThresholdsAdditional.getText());
  }

  public String getFnOutput() {
    return ltfFnOutput.getText();
  }

  public boolean usingParallelProcessing() {
    return true;
  }

  public void expand(final ExpandButton button) {
    if (phSetup.equalsOpenClose(button)) {
      pnlSetupBody.setVisible(button.isExpanded());
    }
    else if (phRunParameters.equalsOpenClose(button)) {
      pnlRunParametersBody.setVisible(button.isExpanded());
    }
    else if (phRunParameters.equalsAdvancedBasic(button)) {
      updateAdvanceRunParameters(button.isExpanded());
    }
    else if (phRun.equalsOpenClose(button)) {
      pnlRunBody.setVisible(button.isExpanded());
    }
    UIHarness.INSTANCE.pack(axisID, manager);
  }

  public String getDirectory() {
    return ftfDirectory.getText();
  }

  public void setDirectory(final String directory) {
    ftfDirectory.setText(directory);
  }

  public void setFnOutput(final String output) {
    ltfFnOutput.setText(output);
  }

  void msgVolumeTableSizeChanged() {
    updateDisplay();
  }

  void setUsingInitMotlFile() {
    rbInitMotlFiles.setSelected(true);
  }

  private void setTooltipText() {
    ftfDirectory
        .setToolTipText("The directory which will contain the .prm file, .epe file, other data files, intermediate files, and results.  "
            + "Only one .epe file per directory.");
    try {
      ReadOnlyAutodoc autodoc = AutodocFactory
          .getInstance(AutodocFactory.PEET_PRM);
      String tooltip = EtomoAutodoc.getTooltip(autodoc,
          MatlabParamFile.INIT_MOTL_KEY);
      rbInitMotlZero.setToolTipText(tooltip);
      rbInitMotlXAndZAxis.setToolTipText(tooltip);
      rbInitMotlZAxis.setToolTipText(tooltip);
      rbInitMotlFiles.setToolTipText(tooltip);
      tooltip = EtomoAutodoc
          .getTooltip(autodoc, MatlabParamFile.TILT_RANGE_KEY);
      cbTiltRange.setToolTipText(tooltip);
      tooltip = EtomoAutodoc.getTooltip(autodoc, MatlabParamFile.REFERENCE_KEY);
      rbReferenceVolume.setToolTipText(tooltip);
      rbReferenceFile.setToolTipText(tooltip);
      sReferenceVolume.setToolTipText(tooltip);
      ltfReferenceParticle.setToolTipText(tooltip);
      ftfReferenceFile.setToolTipText(tooltip);
      ltfEdgeShift.setToolTipText(EtomoAutodoc.getTooltip(autodoc,
          MatlabParamFile.EDGE_SHIFT_KEY));
      tooltip = EtomoAutodoc.getTooltip(autodoc, MatlabParamFile.SZ_VOL_KEY);
      ltfSzVolX.setToolTipText(tooltip);
      ltfSzVolY.setToolTipText(tooltip);
      ltfSzVolZ.setToolTipText(tooltip);
      tooltip = EtomoAutodoc.getTooltip(autodoc, MatlabParamFile.CC_MODE_KEY);
      rbCcModeNormalized.setToolTipText(tooltip);
      rbCcModeLocal.setToolTipText(tooltip);
      tooltip = EtomoAutodoc.getTooltip(autodoc, MatlabParamFile.MEAN_FILL_KEY);
      cbMeanFill.setToolTipText(tooltip);
      tooltip = EtomoAutodoc.getTooltip(autodoc,
          MatlabParamFile.DEBUG_LEVEL_KEY);
      lsDebugLevel.setToolTipText(tooltip);
      tooltip = EtomoAutodoc
          .getTooltip(autodoc, MatlabParamFile.LOW_CUTOFF_KEY);
      ltfLowCutoff.setToolTipText(tooltip);
      tooltip = EtomoAutodoc.getTooltip(autodoc,
          MatlabParamFile.REF_FLAG_ALL_TOM_KEY);
      cbRefFlagAllTom.setToolTipText(tooltip);
      tooltip = EtomoAutodoc.getTooltip(autodoc,
          MatlabParamFile.LST_THRESHOLDS_KEY);
      ltfLstThresholdsStart.setToolTipText(tooltip);
      ltfLstThresholdsIncrement.setToolTipText(tooltip);
      ltfLstThresholdsEnd.setToolTipText(tooltip);
      ltfLstThresholdsAdditional.setToolTipText(tooltip);
      tooltip = EtomoAutodoc.getTooltip(autodoc,
          MatlabParamFile.LST_FLAG_ALL_TOM_KEY);
      cbLstFlagAllTom.setToolTipText(tooltip);
      tooltip = EtomoAutodoc.getTooltip(autodoc,
          MatlabParamFile.PARTICLE_PER_CPU_KEY);
      lsParticlePerCPU.setToolTipText(tooltip);
      tooltip = EtomoAutodoc.getTooltip(autodoc,
          MatlabParamFile.ALIGNED_BASE_NAME_KEY);
      ltfAlignedBaseName.setToolTipText(tooltip);
      tooltip = EtomoAutodoc.getTooltip(autodoc, MatlabParamFile.FN_OUTPUT_KEY);
      ltfFnOutput.setToolTipText(tooltip);
    }
    catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    catch (LogFile.ReadException e) {
      e.printStackTrace();
    }
  }

  private void updateAdvanceRunParameters(boolean advanced) {
    pnlAdvanced.setVisible(advanced);
  }

  private void setDefaults() {
    ltfEdgeShift.setText(MatlabParamFile.EDGE_SHIFT_DEFAULT);
    cbMeanFill.setSelected(MatlabParamFile.MEAN_FILL_DEFAULT);
    ltfLowCutoff.setText(MatlabParamFile.LOW_CUTOFF_DEFAULT);
  }

  private Container createSetupPanel() {
    //body
    pnlSetupBody.setBoxLayout(BoxLayout.Y_AXIS);
    pnlSetupBody.add(ftfDirectory.getContainer());
    pnlSetupBody.add(ltfFnOutput.getContainer());
    pnlSetupBody.add(volumeTable.getContainer());
    //main panel
    SpacedPanel pnlSetup = new SpacedPanel();
    pnlSetup.setBoxLayout(BoxLayout.Y_AXIS);
    pnlSetup.setBorder(BorderFactory.createEtchedBorder());
    pnlSetup.add(phSetup.getContainer());
    pnlSetup.add(pnlSetupBody);
    return pnlSetup.getContainer();
  }

  private Container createRunParametersPanel() {
    //volume reference
    JPanel pnlVolumeReference = new JPanel();
    pnlVolumeReference.setLayout(new BoxLayout(pnlVolumeReference,
        BoxLayout.X_AXIS));
    pnlVolumeReference.add(rbReferenceVolume.getComponent());
    pnlVolumeReference.add(sReferenceVolume.getComponent());
    pnlVolumeReference.add(ltfReferenceParticle.getContainer());
    //volume file
    JPanel pnlVolumeFile = new JPanel();
    pnlVolumeFile.setLayout(new BoxLayout(pnlVolumeFile, BoxLayout.X_AXIS));
    pnlVolumeFile.add(rbReferenceFile.getComponent());
    pnlVolumeFile.add(ftfReferenceFile.getContainer());
    //reference
    JPanel pnlReference = new JPanel();
    pnlReference.setLayout(new BoxLayout(pnlReference, BoxLayout.Y_AXIS));
    pnlReference.setBorder(new EtchedBorder("Reference").getBorder());
    pnlReference.add(pnlVolumeReference);
    pnlReference.add(pnlVolumeFile);
    //szVol
    SpacedPanel pnlSzVol = new SpacedPanel();
    pnlSzVol.setBoxLayout(BoxLayout.X_AXIS);
    pnlSzVol.add(ltfSzVolX.getContainer());
    pnlSzVol.add(ltfSzVolY.getContainer());
    pnlSzVol.add(ltfSzVolZ.getContainer());
    //init MOTL
    JPanel pnlInitMotl = new JPanel();
    pnlInitMotl.setLayout(new BoxLayout(pnlInitMotl, BoxLayout.Y_AXIS));
    pnlInitMotl.setBorder(new EtchedBorder("Initial Motive List").getBorder());
    pnlInitMotl.add(rbInitMotlZero.getComponent());
    pnlInitMotl.add(rbInitMotlZAxis.getComponent());
    pnlInitMotl.add(rbInitMotlXAndZAxis.getComponent());
    pnlInitMotl.add(rbInitMotlFiles.getComponent());
    //tiltRange and edgeShift
    JPanel pnlTiltRange = new JPanel();
    pnlTiltRange.setLayout(new BoxLayout(pnlTiltRange, BoxLayout.X_AXIS));
    pnlTiltRange.add(cbTiltRange);
    pnlTiltRange.add(Box.createRigidArea(FixedDim.x40_y0));
    ltfEdgeShift.setTextPreferredWidth(UIParameters.INSTANCE.getIntegerWidth());
    pnlTiltRange.add(ltfEdgeShift.getContainer());
    //CCMode
    JPanel pnlCcMode = new JPanel();
    pnlCcMode.setLayout(new BoxLayout(pnlCcMode, BoxLayout.Y_AXIS));
    pnlCcMode.setBorder(new EtchedBorder("Cross correlation measure")
        .getBorder());
    pnlCcMode.add(rbCcModeNormalized.getComponent());
    pnlCcMode.add(rbCcModeLocal.getComponent());
    //advanced right panel
    JPanel pnlAdvancedRight = new JPanel();
    pnlAdvancedRight
        .setLayout(new BoxLayout(pnlAdvancedRight, BoxLayout.Y_AXIS));
    pnlAdvancedRight.add(cbMeanFill);
    pnlAdvancedRight.add(ltfAlignedBaseName.getContainer());
    pnlAdvancedRight.add(ltfLowCutoff.getContainer());
    pnlAdvancedRight.add(lsDebugLevel.getContainer());
    //advanced panel
    pnlAdvanced.setLayout(new BoxLayout(pnlAdvanced, BoxLayout.X_AXIS));
    pnlAdvanced.add(pnlCcMode);
    pnlAdvanced.add(Box.createRigidArea(FixedDim.x40_y0));
    pnlAdvanced.add(pnlAdvancedRight);
    //body
    pnlRunParametersBody.setBoxLayout(BoxLayout.Y_AXIS);
    pnlRunParametersBody.setComponentAlignmentX(Component.CENTER_ALIGNMENT);
    pnlRunParametersBody.add(pnlReference);
    pnlRunParametersBody.add(pnlSzVol);
    pnlRunParametersBody.add(pnlInitMotl);
    pnlRunParametersBody.add(pnlTiltRange);
    pnlRunParametersBody.add(pnlAdvanced);
    //main panel
    JPanel pnlRunParameters = new JPanel();
    pnlRunParameters
        .setLayout(new BoxLayout(pnlRunParameters, BoxLayout.Y_AXIS));
    pnlRunParameters.setBorder(BorderFactory.createEtchedBorder());
    pnlRunParameters.add(phRunParameters.getContainer());
    pnlRunParameters.add(pnlRunParametersBody.getContainer());
    return pnlRunParameters;
  }

  private Container createRunPanel() {
    //lstThresholds
    SpacedPanel pnlLstThresholds = new SpacedPanel();
    pnlLstThresholds.setBoxLayout(BoxLayout.X_AXIS);
    pnlLstThresholds.setBorder(new EtchedBorder(
        "Number of Particles in Averages").getBorder());
    pnlLstThresholds.add(ltfLstThresholdsStart.getContainer());
    pnlLstThresholds.add(ltfLstThresholdsIncrement.getContainer());
    pnlLstThresholds.add(ltfLstThresholdsEnd.getContainer());
    pnlLstThresholds.add(ltfLstThresholdsAdditional.getContainer());
    //body
    pnlRunBody.setBoxLayout(BoxLayout.Y_AXIS);
    pnlRunBody.setComponentAlignmentX(Component.CENTER_ALIGNMENT);
    pnlRunBody.add(iterationTable.getContainer());
    pnlRunBody.add(cbRefFlagAllTom);
    pnlRunBody.add(pnlLstThresholds);
    pnlRunBody.add(cbLstFlagAllTom);
    pnlRunBody.add(lsParticlePerCPU);
    btnRun.setSize();
    pnlRunBody.add(btnRun);
    //main panel
    JPanel pnlRun = new JPanel();
    pnlRun.setLayout(new BoxLayout(pnlRun, BoxLayout.Y_AXIS));
    pnlRun.setBorder(BorderFactory.createEtchedBorder());
    pnlRun.add(phRun.getContainer());
    pnlRun.add(pnlRunBody.getContainer());
    return pnlRun;
  }

  private void action(ActionEvent action) {
    String actionCommand = action.getActionCommand();
    if (actionCommand.equals(ftfDirectory.getActionCommand())) {
      chooseDirectory();
    }
    else if (actionCommand.equals(rbInitMotlZero.getActionCommand())) {
      updateDisplay();
    }
    else if (actionCommand.equals(rbInitMotlZAxis.getActionCommand())) {
      updateDisplay();
    }
    else if (actionCommand.equals(rbInitMotlXAndZAxis.getActionCommand())) {
      updateDisplay();
    }
    else if (actionCommand.equals(rbInitMotlFiles.getActionCommand())) {
      updateDisplay();
    }
    else if (actionCommand.equals(rbReferenceVolume.getActionCommand())) {
      updateDisplay();
    }
    else if (actionCommand.equals(rbReferenceFile.getActionCommand())) {
      updateDisplay();
    }
    else if (actionCommand.equals(cbTiltRange.getActionCommand())) {
      updateDisplay();
    }
  }

  private void referenceFileAction() {
    chooseReferenceFile();
  }

  private void updateDisplay() {
    //edge shift
    ltfEdgeShift.setEnabled(cbTiltRange.isSelected());
    //reference
    int size = volumeTable.size();
    boolean enable = size > 1;
    rbReferenceVolume.setEnabled(enable);
    sReferenceVolume.setEnabled(enable && rbReferenceVolume.isSelected());
    sReferenceVolume.setMax(size);
    ltfReferenceParticle.setEnabled(enable && rbReferenceVolume.isSelected());
    ftfReferenceFile.setEnabled(rbReferenceFile.isSelected());
    volumeTable.updateDisplay(rbInitMotlFiles.isSelected(), cbTiltRange
        .isSelected());
  }

  private void chooseDirectory() {
    JFileChooser chooser = new JFileChooser(new File(manager
        .getPropertyUserDir()));
    chooser.setPreferredSize(UIParameters.INSTANCE.getFileChooserDimension());
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    int returnVal = chooser.showOpenDialog(rootPanel);
    if (returnVal == JFileChooser.APPROVE_OPTION) {
      ftfDirectory.setText(chooser.getSelectedFile().getAbsolutePath());
    }
  }

  private void chooseReferenceFile() {
    JFileChooser chooser = new JFileChooser(new File(manager
        .getPropertyUserDir()));
    chooser.setPreferredSize(UIParameters.INSTANCE.getFileChooserDimension());
    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    int returnVal = chooser.showOpenDialog(rootPanel);
    if (returnVal == JFileChooser.APPROVE_OPTION) {
      ftfReferenceFile.setText(chooser.getSelectedFile().getAbsolutePath());
    }
  }

  private void addListeners() {
    PDActionListener actionListener = new PDActionListener(this);
    ftfDirectory.addActionListener(actionListener);
    rbInitMotlZero.addActionListener(actionListener);
    rbInitMotlZAxis.addActionListener(actionListener);
    rbInitMotlXAndZAxis.addActionListener(actionListener);
    rbInitMotlFiles.addActionListener(actionListener);
    rbReferenceVolume.addActionListener(actionListener);
    rbReferenceFile.addActionListener(actionListener);
    ftfReferenceFile.addActionListener(new ReferenceFileActionListener(this));
    cbTiltRange.addActionListener(actionListener);
  }

  private class PDActionListener implements ActionListener {
    private final PeetDialog peetDialog;

    private PDActionListener(final PeetDialog peetDialog) {
      this.peetDialog = peetDialog;
    }

    public void actionPerformed(final ActionEvent event) {
      peetDialog.action(event);
    }
  }

  private class ReferenceFileActionListener implements ActionListener {
    private final PeetDialog peetDialog;

    private ReferenceFileActionListener(final PeetDialog peetDialog) {
      this.peetDialog = peetDialog;
    }

    public void actionPerformed(final ActionEvent event) {
      peetDialog.referenceFileAction();
    }
  }
}
