package etomo.ui.swing;

import etomo.BaseManager;
import etomo.storage.Node;
import etomo.type.AxisID;
import etomo.type.ConstEtomoVersion;
import etomo.type.EtomoNumber;

/**
* <p>Description: </p>
* 
* <p>Copyright: Copyright 2010</p>
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
final class GpuTable extends CpuTable {
  public static final String rcsid = "$Id$";

  private static final String PREPEND = "ProcessorTable.Gpu";

  GpuTable(final BaseManager manager, final ParallelPanel parent,
      final AxisID axisID) {
    super(manager, parent, axisID);
  }


  String getheader1NumberCPUsTitle() {
    return "# GPUs";
  }
  
  String getStorePrepend() {
    return PREPEND;
  }

  String getLoadPrepend(ConstEtomoVersion version) {
    return PREPEND;
  }

  String getHeader1ComputerText() {
    return "GPU";
  }

  boolean isExcludeNode(final Node node) {
    if (!node.isGpu()) {
      return true;
    }
    if (node.isGpuLocal()
        && !node.isLocalHost(manager, axisID, manager.getPropertyUserDir())) {
      return true;
    }
    return false;
  }

  ProcessorTableRow createProcessorTableRow(
      final ProcessorTable processorTable, final Node node,
      final EtomoNumber number) {
    return ProcessorTableRow.getComputerInstance(processorTable, node, 1);
  }
  
  void initRow(ProcessorTableRow row) {
    row.turnOffLoadWarning();
  }
}
