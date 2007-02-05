package etomo.type;

import java.util.Properties;

import etomo.storage.Storable;

/**
 * <p>Description: </p>
 * 
 * <p>Copyright: Copyright (c) 2002, 2003, 2004</p>
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
 * <p> Revision 1.1  2005/01/10 23:49:41  sueh
 * <p> bug# 578 A class to parse and compare version numbers.
 * <p> </p>
 */
public final class EtomoVersion implements ConstEtomoVersion, Storable {
  public static final String rcsid = "$Id$";

  public static final String DEFAULT_KEY = "Version";
  private String sectionList[] = null;
  private String key;
  private boolean debug = false;

  public static EtomoVersion getDefaultInstance() {
    return new EtomoVersion();
  }
  
  public static EtomoVersion getDefaultInstance(String version) {
    EtomoVersion instance = new EtomoVersion();
    instance.set(version);
    return instance;
  }
  
  public static EtomoVersion getInstance(String key) {
    EtomoVersion instance = new EtomoVersion();
    instance.key = key;
    return instance;
  }
  
  public static EtomoVersion getInstance(String key, String version) {
    EtomoVersion instance = getInstance(key);
    instance.set(version);
    return instance;
  }
  
  public String getKey() {
    return key;
  }
  
  public void setDebug(boolean debug) {
    this.debug=debug;
  }
  
  private EtomoVersion() {
    key = DEFAULT_KEY;
  }

  private boolean equals(EtomoVersion version) {
    //treat null as the earliest version
    if ((version == null||version.isNull())&&isNull()) {
      return true;
    }
    if (version == null||version.isNull()||isNull()) {
      return false;
    }
    if (sectionList.length!=version.sectionList.length) {
      return false;
    }
    for (int i = 0;i<sectionList.length;i++) {
      if (!sectionList[i].equals(version.sectionList[i])) {
        return false;
      }
    }
    return true;
  }
  
  /**
   * @param version
   * @return true if this is less then version
   */
  public boolean lt(EtomoVersion version) {
    //treat null as the earliest version
    if (isNull()&&(version != null&&!version.isNull())) {
      return true;
    }
    if (version == null||version.isNull()||isNull()) {
      return false;
    }
    int length = Math.min(sectionList.length,version.sectionList.length);
    //loop until a section is not equal then corresponding version section
    for (int i=0;i<length;i++) {
      if(sectionList[i].compareTo(version.sectionList[i])>0) {
        return false;
      }
      if(sectionList[i].compareTo(version.sectionList[i])<0) {
        return true;
      }
    }
    //equal so far - shorter one is less then
    if (sectionList.length<version.sectionList.length) {
      return true;
    }
    return false;
  }
  
  /**
   * @param version
   * @return true if this is less then or equal to version
   */
  public boolean le(EtomoVersion version) {
    //treat null as the earliest version
    if (isNull()) {
      return true;
    }
    if (version == null||version.isNull()) {
      return false;
    }
    int length = Math.min(sectionList.length,version.sectionList.length);
    //loop until a section is not equal then corresponding version section
    for (int i=0;i<length;i++) {
      if(sectionList[i].compareTo(version.sectionList[i])>0) {
        return false;
      }
      if(sectionList[i].compareTo(version.sectionList[i])<0) {
        return true;
      }
    }
    //equal so far - shorter one is less then
    if (sectionList.length<=version.sectionList.length) {
      return true;
    }
    return false;
  }
  
  public boolean gt(EtomoVersion version) {
    //treat null as the earliest version
    if ((version == null||version.isNull())&&!isNull()) {
      return true;
    }
    if (version == null||version.isNull()||isNull()) {
      return false;
    }
    int length = Math.min(sectionList.length,version.sectionList.length);
    //loop until a section is not equal then corresponding version section
    for (int i=0;i<length;i++) {
      if(sectionList[i].compareTo(version.sectionList[i])>0) {
        return true;
      }
      if(sectionList[i].compareTo(version.sectionList[i])<0) {
        return false;
      }
    }
    //equal so far - longer one is greater then
    if (sectionList.length>version.sectionList.length) {
      return true;
    }
    return false;
  }
  
  /**
   * @param version
   * @return true if this is greater then or equal to version
   */
  public boolean ge(EtomoVersion version) {
    //treat null as the earliest version
    if (version == null||version.isNull()) {
      return true;
    }
    if (isNull()) {
      return false;
    }
    int length = Math.min(sectionList.length,version.sectionList.length);
    //loop until a section is not equal then corresponding version section
    for (int i=0;i<length;i++) {
      if(sectionList[i].compareTo(version.sectionList[i])>0) {
        return true;
      }
      if(sectionList[i].compareTo(version.sectionList[i])<0) {
        return false;
      }
    }
    //equal so far - longer one is greater then
    if (sectionList.length>=version.sectionList.length) {
      return true;
    }
    return false;
  }
  
  public boolean ge(ConstEtomoVersion version) {
    return ge((EtomoVersion) version);
  }

  public String toString() {
    if (isNull()) {
      return "";
    }
    StringBuffer buffer = new StringBuffer(sectionList[0]);
    for (int i = 1; i < sectionList.length; i++) {
      buffer.append("." + sectionList[i]);
    }
    return buffer.toString();
  }

  public void store(Properties props) {
    if (isNull()) {
      props.remove(key);
    }
    else {
      props.setProperty(key, toString());
    }
  }

  public void store(Properties props, String prepend) {
    if (debug) {
      System.err.println("store:prepend="+prepend+",key="+key+",toString()="+toString());
    }
    if (isNull()) {
      if (debug) {
        System.err.println("isNull");
      }
      props.remove(prepend + '.' + key);
    }
    else {
      props.setProperty(prepend + "." + key, toString());
    }
    if (debug) {
      System.err.println("props:"+props.getProperty(prepend + "." + key));
    }
  }

  boolean isNull() {
    if (sectionList == null || sectionList.length == 0) {
      return true;
    }
    return false;
  }

  /**
   * Split the parameter version into a list by ".".  Ignore any empty sections.
   * @param version
   */
  public void set(String version) {
    if (version == null || version.matches("\\s*")) {
      sectionList = null;
      return;
    }
    String[] stringList = version.trim().split("\\.");
    if (stringList == null || stringList.length == 0) {
      sectionList = null;
    }
    sectionList = new String[stringList.length];
    for (int i = 0; i < sectionList.length; i++) {
      String section = stringList[i].trim();
      //Ignore empty sections
      if (section != null && !section.equals("") && !section.matches("\\s+")) {
        sectionList[i] = stringList[i].trim();
      }
    }
  }

  public void set(EtomoVersion that) {
    if (that.isNull()) {
      sectionList = null;
    }
    else {
      sectionList = new String[that.sectionList.length];
      for (int i = 0; i < sectionList.length; i++) {
        sectionList[i] = that.sectionList[i];
      }
    }
  }

  public void load(Properties props) {
    set(props.getProperty(key));
  }

  public void load(Properties props, String prepend) {
    set(props.getProperty(prepend + "." + key));
  }

  public void reset() {
    sectionList = null;
  }
}
