package etomo.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import etomo.ApplicationManager;
import etomo.comscript.TrimvolParam;
/**
 * <p>Description: </p>
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
 * <p> Revision 1.4  2003/04/14 23:57:44  rickg
 * <p> In progress
 * <p>
 * <p> Revision 1.3  2003/04/14 04:31:31  rickg
 * <p> In progres
 * <p>
 * <p> Revision 1.2  2003/04/10 23:42:51  rickg
 * <p> In progress
 * <p>
 * <p> Revision 1.1  2003/04/09 23:37:20  rickg
 * <p> In progress
 * <p> </p>
 */

public class TrimvolPanel {
  public static final String rcsid =
    "$Id$";

  private ApplicationManager applicationManager;

  private JPanel pnlTrimvol = new JPanel();

  private JPanel pnlRange = new JPanel();
  private LabeledTextField ltfXMin = new LabeledTextField("X min: ");
  private LabeledTextField ltfXMax = new LabeledTextField(" X max: ");
  private LabeledTextField ltfYMin = new LabeledTextField("Y min: ");
  private LabeledTextField ltfYMax = new LabeledTextField(" Y max: ");
  private LabeledTextField ltfZMin = new LabeledTextField("Z min: ");
  private LabeledTextField ltfZMax = new LabeledTextField(" Z max: ");

  private JPanel pnlScale = new JPanel();
  private JPanel pnlScaleFixed = new JPanel();
  private JRadioButton rbScaleFixed = new JRadioButton("Fixed scaling  ");
  private LabeledTextField ltfFixedScaleMin = new LabeledTextField("min: ");
  private LabeledTextField ltfFixedScaleMax = new LabeledTextField(" max: ");

  private JRadioButton rbScaleSection = new JRadioButton("Section based  ");
  private JPanel pnlScaleSection = new JPanel();
  private LabeledTextField ltfSectionScaleMin = new LabeledTextField("min: ");
  private LabeledTextField ltfSectionScaleMax = new LabeledTextField(" max: ");

  private JCheckBox cbSwapYZ = new JCheckBox("Swap Y and Z dimensions");

  private JPanel pnlButton = new JPanel();
  private JButton btnTrimvol = new JButton("<html><b>Trim Volume</b>");
  private JButton btnImodVolume = new JButton("<html><b>Imod volume</b>");

  /**
   * Default constructor
   */
  public TrimvolPanel(ApplicationManager appMgr) {

    applicationManager = appMgr;
    //  Get the current text height from one of the 
    double height = cbSwapYZ.getPreferredSize().getHeight();

    //  Set the button sizes
    Dimension dimButton = new Dimension();
    dimButton.setSize(8 * height, 2 * height);
    btnTrimvol.setPreferredSize(dimButton);
    btnTrimvol.setMaximumSize(dimButton);
    btnImodVolume.setPreferredSize(dimButton);
    btnImodVolume.setMaximumSize(dimButton);

    //  Layout the range panel
    pnlRange.setLayout(new GridLayout(3, 2));
    pnlRange.setBorder(new EtchedBorder("Volume range").getBorder());

    pnlRange.add(ltfXMin.getContainer());
    pnlRange.add(ltfXMax.getContainer());
    pnlRange.add(ltfYMin.getContainer());
    pnlRange.add(ltfYMax.getContainer());
    pnlRange.add(ltfZMin.getContainer());
    pnlRange.add(ltfZMax.getContainer());

    //  Layout the scale panel
    pnlScaleFixed.setLayout(new BoxLayout(pnlScaleFixed, BoxLayout.X_AXIS));
    pnlScaleFixed.add(rbScaleFixed);
    pnlScaleFixed.add(ltfFixedScaleMin.getContainer());
    pnlScaleFixed.add(ltfFixedScaleMax.getContainer());

    pnlScaleSection.setLayout(new BoxLayout(pnlScaleSection, BoxLayout.X_AXIS));
    pnlScaleSection.add(rbScaleSection);
    pnlScaleSection.add(ltfSectionScaleMin.getContainer());
    pnlScaleSection.add(ltfSectionScaleMax.getContainer());

    ButtonGroup bgScale = new ButtonGroup();
    bgScale.add(rbScaleFixed);
    bgScale.add(rbScaleSection);

    pnlScale.setLayout(new BoxLayout(pnlScale, BoxLayout.Y_AXIS));
    pnlScale.setBorder(new EtchedBorder("Scaling").getBorder());

    pnlScale.add(pnlScaleFixed);
    pnlScale.add(pnlScaleSection);

    pnlButton.setLayout(new BoxLayout(pnlButton, BoxLayout.X_AXIS));
    pnlButton.add(Box.createHorizontalGlue());
    pnlButton.add(btnTrimvol);
    pnlButton.add(Box.createHorizontalGlue());
    pnlButton.add(btnImodVolume);
    pnlButton.add(Box.createHorizontalGlue());

    pnlTrimvol.setLayout(new BoxLayout(pnlTrimvol, BoxLayout.Y_AXIS));
    pnlTrimvol.setBorder(new BeveledBorder("Volume trimming").getBorder());

    pnlTrimvol.add(pnlRange);
    pnlTrimvol.add(Box.createRigidArea(FixedDim.x0_y10));
    pnlTrimvol.add(pnlScale);
    pnlTrimvol.add(Box.createRigidArea(FixedDim.x0_y10));
    cbSwapYZ.setAlignmentX(Component.RIGHT_ALIGNMENT);
    pnlTrimvol.add(cbSwapYZ);
    pnlTrimvol.add(Box.createRigidArea(FixedDim.x0_y10));
    pnlTrimvol.add(pnlButton);

    RadioButtonActonListener radioButtonActonListener =
      new RadioButtonActonListener(this);
    rbScaleFixed.addActionListener(radioButtonActonListener);
    rbScaleSection.addActionListener(radioButtonActonListener);

    ButtonActonListener buttonActonListener = new ButtonActonListener(this);
    btnTrimvol.addActionListener(buttonActonListener);
    btnImodVolume.addActionListener(buttonActonListener);
  }

  /**
   * Return the container of the panel
   * @return
   */
  public Container getContainer() {
    return pnlTrimvol;
  }

  /**
   * Set the panel values with the specified parameters
   * @param trimvolParam
   */
  public void setParameters(TrimvolParam trimvolParam) {
    ltfXMin.setText(trimvolParam.getXMin());
    ltfXMax.setText(trimvolParam.getXMax());
    ltfYMin.setText(trimvolParam.getYMin());
    ltfYMax.setText(trimvolParam.getYMax());
    ltfZMin.setText(trimvolParam.getZMin());
    ltfZMax.setText(trimvolParam.getZMax());
    cbSwapYZ.setSelected(trimvolParam.isSwapYZ());

    if (trimvolParam.isFixedScaling()) {
      ltfFixedScaleMin.setText(trimvolParam.getFixedScaleMin());
      ltfFixedScaleMax.setText(trimvolParam.getFixedScaleMax());
      rbScaleFixed.setSelected(true);
    }
    else {
      ltfSectionScaleMin.setText(trimvolParam.getSectionScaleMin());
      ltfSectionScaleMax.setText(trimvolParam.getSectionScaleMax());
      rbScaleSection.setSelected(true);
    }
    setScaleState();
  }

  /**
   * Get the parameter values from the panel 
   * @param trimvolParam
   */
  public void getParameters(TrimvolParam trimvolParam) {
    trimvolParam.setXMin(Integer.parseInt(ltfXMin.getText()));
    trimvolParam.setXMax(Integer.parseInt(ltfXMax.getText()));
    trimvolParam.setYMin(Integer.parseInt(ltfYMin.getText()));
    trimvolParam.setYMax(Integer.parseInt(ltfYMax.getText()));
    trimvolParam.setZMin(Integer.parseInt(ltfZMin.getText()));
    trimvolParam.setZMax(Integer.parseInt(ltfZMax.getText()));
    trimvolParam.setSwapYZ(cbSwapYZ.isSelected());
    
    if (rbScaleFixed.isSelected()) {
      trimvolParam.setFixedScaling(true);
      trimvolParam.setFixedScaleMin(
        Integer.parseInt(ltfFixedScaleMin.getText()));
      trimvolParam.setFixedScaleMax(
        Integer.parseInt(ltfFixedScaleMax.getText()));
    }
    else {
      trimvolParam.setFixedScaling(false);
      trimvolParam.setSectionScaleMin(
        Integer.parseInt(ltfSectionScaleMin.getText()));
      trimvolParam.setSectionScaleMax(
        Integer.parseInt(ltfSectionScaleMax.getText()));
    }
  }

  /**
   * Enable/disable the appropriate text fields for the scale section
   *
   */
  private void setScaleState() {
    if (rbScaleFixed.isSelected()) {
      ltfFixedScaleMin.setEnabled(true);
      ltfFixedScaleMax.setEnabled(true);
      ltfSectionScaleMin.setEnabled(false);
      ltfSectionScaleMax.setEnabled(false);
    }
    else {
      ltfFixedScaleMin.setEnabled(false);
      ltfFixedScaleMax.setEnabled(false);
      ltfSectionScaleMin.setEnabled(true);
      ltfSectionScaleMax.setEnabled(true);
    }
  }

  /**
   * Call setScaleState when the radio buttons change
   * @param event
   */
  private void manageRadioButtonState(ActionEvent event) {
    setScaleState();
  }

  private void buttonAction(ActionEvent event) {
    if (event.getActionCommand() == btnTrimvol.getActionCommand()) {
      applicationManager.trimVolume();
    }

    if (event.getActionCommand() == btnImodVolume.getActionCommand()) {
      //applicationManager.imodVolume();
    }

  }
  /**
   * An inner class to manage the scale radio buttons 
   */
  class RadioButtonActonListener implements ActionListener {
    TrimvolPanel listenee;

    RadioButtonActonListener(TrimvolPanel TrimvolPanel) {
      listenee = TrimvolPanel;
    }

    public void actionPerformed(ActionEvent event) {
      listenee.manageRadioButtonState(event);
    }
  }

  class ButtonActonListener implements ActionListener {
    TrimvolPanel listenee;

    ButtonActonListener(TrimvolPanel TrimvolPanel) {
      listenee = TrimvolPanel;
    }

    public void actionPerformed(ActionEvent event) {
      listenee.buttonAction(event);
    }

  }
}