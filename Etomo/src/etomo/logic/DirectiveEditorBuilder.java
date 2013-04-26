package etomo.logic;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import etomo.BaseManager;
import etomo.storage.ComFile;
import etomo.storage.Directive;
import etomo.storage.DirectiveDescrFile;
import etomo.storage.DirectiveDescrSection;
import etomo.storage.DirectiveMap;
import etomo.storage.DirectiveName;
import etomo.storage.DirectiveType;
import etomo.storage.LogFile;
import etomo.storage.autodoc.AutodocFactory;
import etomo.storage.autodoc.ReadOnlyStatement;
import etomo.storage.autodoc.ReadOnlyStatementList;
import etomo.storage.autodoc.Statement;
import etomo.storage.autodoc.StatementLocation;
import etomo.type.AxisID;
import etomo.type.AxisType;
import etomo.type.DirectiveFileType;

/**
* <p>Description: </p>
* 
* <p>Copyright: Copyright 2013</p>
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
public final class DirectiveEditorBuilder {
  public static final String rcsid = "$Id:$";

  private static final String SECTION_OTHER_HEADER = "Other Directives";
  private static final AxisID AXID_ID = AxisID.ONLY;

  private final DirectiveMap directiveMap = new DirectiveMap();
  private final List<DirectiveDescrSection> sectionArray = new ArrayList<DirectiveDescrSection>();
  private boolean[] fileTypeExists = new boolean[DirectiveFileType.NUM];

  private final BaseManager manager;

  private boolean debug = false;
  private DirectiveDescrSection otherSection = null;

  public DirectiveEditorBuilder(final BaseManager manager) {
    this.manager = manager;
  }

  /**
   * Builds a list of directives.  Gets the directive names and descriptions from
   * directives.csv and the local directive file matching the type member variable.  Gets
   * the values and default values from ApplicationManager and the .com and origcoms/.com
   * files.
   * @param errmsg - may be null
   * @return errmsg
   */
  public StringBuffer build(final AxisType sourceAxisType, StringBuffer errmsg) {
    // reset
    directiveMap.clear();
    sectionArray.clear();
    for (int i = 0; i < DirectiveFileType.NUM; i++) {
      fileTypeExists[i] = false;
    }
    otherSection = null;
    // Load and update directives, and load sections.
    if (errmsg == null) {
      errmsg = new StringBuffer();
    }
    Directive directive = null;
    // Load the directives from directives.csv.
    DirectiveDescrFile.Iterator descrIterator = DirectiveDescrFile.INSTANCE.getIterator(
        manager, AXID_ID);
    if (descrIterator != null) {
      // Skip title and column header
      descrIterator.hasNext();
      descrIterator.hasNext();
      DirectiveDescrSection section = null;
      int dCount = 0;
      while (descrIterator.hasNext()) {
        descrIterator.next();
        // Get the section - directives following this section are associated with it.
        if (descrIterator.isSection()) {
          if (section != null && dCount == 0) {
            sectionArray.remove(section);
          }
          section = new DirectiveDescrSection(descrIterator.getSectionHeader());
          dCount = 0;
          sectionArray.add(section);
        }
        else if (descrIterator.isDirective()) {
          // Get the directive
          directive = new Directive(descrIterator.getDirectiveDescription());
          if (directive.isValid()) {
            dCount++;
            // Store the directive in the map
            directiveMap.put(directive.getKey(), directive);
            // Store the directive under current section
            if (section == null) {
              // This shouldn't happen because all directives in directives.csv are listed
              // under a header.
              if (otherSection == null) {
                otherSection = new DirectiveDescrSection(SECTION_OTHER_HEADER);
              }
              section = otherSection;
            }
            section.add(directive);
          }
        }
      }
      DirectiveDescrFile.INSTANCE.releaseIterator(descrIterator);
    }
    // Add information and undocumented directives from the current directive files.
    updateFromLocalDirectiveFile(DirectiveFileType.SCOPE, errmsg);
    updateFromLocalDirectiveFile(DirectiveFileType.SYSTEM, errmsg);
    updateFromLocalDirectiveFile(DirectiveFileType.USER, errmsg);
    updateFromLocalDirectiveFile(DirectiveFileType.BATCH, errmsg);
    // update setupset and runtime
    manager.updateDirectiveMap(directiveMap, errmsg);
    // update paramMap from *.com and origcoms/*.com
    // Get a sorted list of comparam directive names
    DirectiveMap.Iterator iterator = directiveMap.keySet(DirectiveType.COM_PARAM)
        .iterator();
    boolean dualAxis = sourceAxisType == AxisType.DUAL_AXIS;
    AxisID firstAxisID;
    if (dualAxis) {
      firstAxisID = AxisID.FIRST;
    }
    else {
      firstAxisID = AxisID.ONLY;
    }
    // A axis
    AxisID curAxisID = firstAxisID;
    ComFile comFileA = new ComFile(manager, curAxisID);
    Map<String, String> commandMapA = null;
    ComFile comFileADefaults = new ComFile(manager, curAxisID, "origcoms");
    Map<String, String> commandMapADefaults = null;
    // B axis
    ComFile comFileB = null;
    Map<String, String> commandMapB = null;
    ComFile comFileBDefaults = null;
    Map<String, String> commandMapBDefaults = null;
    if (dualAxis) {
      curAxisID = AxisID.SECOND;
      comFileB = new ComFile(manager, curAxisID);
      comFileBDefaults = new ComFile(manager, curAxisID, "origcoms");
    }
    curAxisID = firstAxisID;
    DirectiveName directiveName = new DirectiveName();
    while (iterator.hasNext()) {
      directiveName.setKey(iterator.next());
      // save axis A value
      commandMapA = getCommandMap(directiveName, comFileA, curAxisID, commandMapA, errmsg);
      if (commandMapA != null) {
        setDirectiveValue(commandMapA, directiveMap, directiveName, curAxisID, false);
      }
      // save axis A default value
      commandMapADefaults = getCommandMap(directiveName, comFileADefaults, curAxisID,
          commandMapADefaults, errmsg);
      if (commandMapADefaults != null) {
        setDirectiveValue(commandMapADefaults, directiveMap, directiveName, curAxisID,
            true);
      }
      if (dualAxis) {
        curAxisID = AxisID.SECOND;
        // save axis B value
        commandMapB = getCommandMap(directiveName, comFileB, curAxisID, commandMapB,
            errmsg);
        if (commandMapB != null) {
          setDirectiveValue(commandMapB, directiveMap, directiveName, curAxisID, false);
        }
        // save axis B default value
        commandMapBDefaults = getCommandMap(directiveName, comFileBDefaults, curAxisID,
            commandMapBDefaults, errmsg);
        if (commandMapBDefaults != null) {
          setDirectiveValue(commandMapBDefaults, directiveMap, directiveName, curAxisID,
              true);
        }
      }
      curAxisID = firstAxisID;
    }
    return errmsg;
  }

  /**
   * Updates existing directives and loads ones that are not in directive.csv
   * @param type
   * @param errmsg for errors - must not be null
   * @return true if the local directive file exists
   */
  private boolean updateFromLocalDirectiveFile(final DirectiveFileType type,
      final StringBuffer errmsg) {
    Directive directive = null;
    File directiveFile = null;
    if (type != null && (directiveFile = type.getLocalFile(manager, AXID_ID)) != null
        && directiveFile.exists()) {
      fileTypeExists[type.getIndex()] = true;
      try {
        ReadOnlyStatementList statementList = AutodocFactory.getInstance(manager,
            directiveFile);
        if (statementList != null) {
          StatementLocation location = statementList.getStatementLocation();
          ReadOnlyStatement statement = null;
          DirectiveName directiveName = new DirectiveName();
          while ((statement = statementList.nextStatement(location)) != null) {
            if (statement.getType() == Statement.Type.NAME_VALUE_PAIR) {
              AxisID axisID = directiveName.setKey(statement.getLeftSide());
              directive = directiveMap.get(directiveName.getKey());
              if (directive == null) {
                // Handle undefined directives.
                directive = new Directive(directiveName);
                if (otherSection == null) {
                  otherSection = new DirectiveDescrSection(SECTION_OTHER_HEADER);
                }
                otherSection.add(directive.getKey());
                directiveMap.put(directive.getKey(), directive);
              }
              directive.setInDirectiveFile(type, axisID, true);
            }
          }
          return true;
        }
      }
      catch (IOException e) {
        e.printStackTrace();
        errmsg.append("Unable to load " + directiveFile.getName() + ".  "
            + e.getMessage() + "  ");
      }
      catch (LogFile.LockException e) {
        errmsg.append("Unable to load " + directiveFile.getName() + ".  "
            + e.getMessage() + "  ");
      }
    }
    return false;
  }

  /**
   * Uses the directiveName as a guide and decides whether to open a com file or create a
   * new commandMap.  If neither of these things are necessary, returns the existing
   * commandMap.
   * @param directiveName
   * @param comFile
   * @param axisID
   * @param commandMap
   * @return
   */
  private Map<String, String> getCommandMap(final DirectiveName directiveName,
      final ComFile comFile, final AxisID axisID, Map<String, String> commandMap,
      final StringBuffer errmsg) {
    // Get a new comfile and origcoms comfile each time the comfile name changes in the
    // sorted list of comparam directives.
    String comFileName = directiveName.getComFileName();
    if (!comFile.equalsComFileName(comFileName)) {
      comFile.setComFileName(comFileName);
    }
    // Get a new program from the comfile and origcoms comfile each time the program
    // name or comfile name changes in the sorted list of comparam directives.
    String programName = directiveName.getProgramName();
    if (!comFile.equalsProgramName(programName)) {
      commandMap = comFile.getCommandMap(programName, errmsg);
    }
    return commandMap;
  }

  /**
   * Using directiveName as the key, gets a value from commandMap and gets a directive
   * from directiveMap.  Places the value in the directive.
   * @param commandMap - list of values
   * @param directiveMap - list of directives - must not be null
   * @param directiveName - key
   * @param axisID - the value belongs to a specific axis
   * @param isDefaultValue - the value is a default value
   */
  private void setDirectiveValue(final Map<String, String> commandMap,
      final DirectiveMap directiveMap, final DirectiveName directiveName,
      final AxisID axisID, final boolean isDefaultValue) {
    if (commandMap == null) {
      return;
    }
    // Pull out the value and default value from the program commands.
    if (commandMap.containsKey(directiveName.getParameterName())) {
      Directive directive = directiveMap.get(directiveName.getKey());
      // Set value in the directive
      String value = commandMap.get(directiveName.getParameterName());
      if (value != null) {
        if (!isDefaultValue) {
          directive.setValue(axisID, value);
        }
        else {
          directive.setDefaultValue(axisID, value);
        }
      }
      else {
        // no value - treat as a boolean
        if (!isDefaultValue) {
          directive.setValue(axisID, true);
        }
        else {
          directive.setDefaultValue(axisID, true);
        }
      }
    }
  }

  public DirectiveDescrSection getOtherSection() {
    return otherSection;
  }

  public List<DirectiveDescrSection> getSectionArray() {
    return sectionArray;
  }

  public boolean[] getFileTypeExists() {
    return fileTypeExists;
  }

  public DirectiveMap getDirectiveMap() {
    return directiveMap;
  }
}
