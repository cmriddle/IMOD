/*
 *  beadfix.c -- Special module for fixing fiducial models
 *
 *
 *  Copyright (C) 1995-2005 by Boulder Laboratory for 3-Dimensional Electron
 *  Microscopy of Cells ("BL3DEMC") and the Regents of the University of 
 *  Colorado.  See dist/COPYRIGHT for full copyright notice.
 */
/*  $Author$

    $Date$

    $Revision$

    Log at end of file
*/

/* include needed Qt headers and imod headers
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <qpushbutton.h>
#include <qcheckbox.h>
#include <qspinbox.h>
#include <qradiobutton.h>
#include <qtooltip.h>
#include <qtoolbutton.h>
#include <qhbox.h>
#include <qvbuttongroup.h>
#include <qlabel.h>
#include <qlayout.h>
#include <qdir.h>
#include <qstringlist.h>
#include <qfile.h>
#include <qprocess.h>


/*#include "../../imod/imod.h"
#include "../../imod/imodplug.h"
#include "../../imod/control.h" */

// To make internal, 
// 1) change from imodplugin.h (or whatever that ends up as) 
// to imod.h and control.h
//#include "imodplugin.h"
#include "imod.h"
#include "imod_client_message.h"
#include "control.h"
#include "dia_qtutils.h"
#include "beadfix.h"
#include "pegged.xpm"
#include "unpegged.xpm"
#include "imod_input.h"
#include "preferences.h"
#include "undoredo.h"

// 2) Declare the internal functions as static
// And set them into the member variables in the constructor
static char *imodPlugInfo(int *type);
static int imodPlugKeys(ImodView *vw, QKeyEvent *event);
static void imodPlugExecute(ImodView *inImodView);
static  int imodPlugExecuteMessage(ImodView *vw, QStringList *strings,
                                   int *arg);
static int imodPlugMouse(ImodView *vw, QMouseEvent *event, float imx,
                         float imy, int but1, int but2, int but3);

enum {SEED_MODE = 0, GAP_MODE, RES_MODE};

BeadFixerModule::BeadFixerModule()
{
  mInfo = imodPlugInfo;
  mExecuteType = NULL;
  mExecute = imodPlugExecute;
  mExecuteMessage = imodPlugExecuteMessage;
  mKeys = imodPlugKeys;
  mMouse = imodPlugMouse;
}

#define MAXLINE 100
#define MAX_DIAMETER 50
#define MAX_OVERLAY 20
#define NUM_SAVED_VALS 10

/*
 *  Define a structure to contain all local plugin data.
 */
typedef struct
{
  ImodView    *view;
  BeadFixer *window;
  int    left, top;                     /* Last window position */
  int    autoCenter;
  int    lightBead;
  int    diameter;
  int    overlayOn;
  int    overlaySec;
  int    showMode;
  int    reverseOverlay;
  int    autoNewCont;
  char   *filename;
}PlugData;


static PlugData thisPlug = { NULL, NULL, 0, 0, 0, 0, 10, 0, 4, 0, 0, NULL };
static PlugData *plug = &thisPlug;

#define ERROR_NO_IMOD_DIR -64352



/*
 * Called by the imod plugin load function. 
 */
char *imodPlugInfo(int *type)
{
  if (type)
    *type = IMOD_PLUG_MENU + IMOD_PLUG_KEYS + IMOD_PLUG_MESSAGE + 
      IMOD_PLUG_MOUSE;
  return("Bead Fixer");
}

/*
 *  Grab hotkey input. return 1 if we handle the key.
 */
int imodPlugKeys(ImodView *vw, QKeyEvent *event)
{
  int keysym;
  int    keyhandled = 0;
  int    ctrl;
  int    shift;

  /*
   * Don't grab keys if plug window isn't open.
   */
  if (!plug->view)
    return 0;
    
  /* The keysym values are Key_A ...
   * Key_Space, Key_Comma...
   */
  keysym = event->key();

  /*
   * Modifier key mask.  Set ctrl and shift to true
   * if the coresponding key is pressed.
   */
  ctrl   = event->state() & Qt::ControlButton;
  shift  = event->state() & Qt::ShiftButton;
    
    
  switch(keysym){
  case Qt::Key_Apostrophe: 
    if (plug->showMode != RES_MODE)
      break;
    keyhandled = 1;
    plug->window->nextRes();
    break;
  case Qt::Key_QuoteDbl: 
    if (plug->showMode != RES_MODE)
      break;
    keyhandled = 1;
    plug->window->backUp();
    break;
  case Qt::Key_Space:
    if (plug->showMode != GAP_MODE)
      break;
    keyhandled = 1;
    plug->window->nextGap();
    break;
  case Qt::Key_Semicolon:
    if (plug->showMode != RES_MODE)
      break;
    keyhandled = 1;
    plug->window->movePoint();
    break;
  case Qt::Key_Colon:
    if (plug->showMode != RES_MODE)
      break;
    keyhandled = 1;
    plug->window->moveAll();
    break;
  case Qt::Key_U:
    if (plug->showMode != RES_MODE)
      break;
    keyhandled = 1;
    plug->window->undoMove();
    break;
  case Qt::Key_Slash:
    if (plug->showMode != SEED_MODE)
      break;
    keyhandled = 1;
    plug->overlayOn = 1 - plug->overlayOn;
    diaSetChecked(plug->window->overlayBox, plug->overlayOn != 0);
    plug->window->overlayToggled(plug->overlayOn != 0);
    break;
  default:
    break;
  }
  return keyhandled;
}

static int imodPlugMouse(ImodView *vw, QMouseEvent *event, float imx,
                         float imy, int but1, int but2, int but3)
{
  int keysym;
  int handled = 0;

  // Reject event if window not open or not in model mode
  if (!plug->view || !ivwGetMovieModelMode(vw))
    return 0;

  // insert point (potentially) for middle button,
  // Modify point for shift right if autocenter on
  if (event->type() == QEvent::MouseButtonPress && but2 != 0)
    handled = plug->window->insertPoint(imx, imy);
  else if (event->type() == QEvent::MouseButtonPress && but3 != 0 &&
           (event->state() & Qt::ShiftButton) && plug->autoCenter && 
           plug->showMode != RES_MODE)
    handled = plug->window->modifyPoint(imx, imy);
  return handled;
}

/*
 *  Execute any function or program you wish with this function.
 *  Here we open up a window for user interaction.
 *  see imodplug.h for a list of support functions.
 */

#define loadSaved(a,b) if (nvals > (b)) (a) = (int)(savedValues[(b)] + 0.01);

void imodPlugExecute(ImodView *inImodView)
{
  double savedValues[NUM_SAVED_VALS];
  int nvals;
  static int firstTime = 1;

  if (plug->window){
    /* 
     * Bring the window to the front if already open.
     */
    plug->window->raise();
    return;
  }

  plug->view = inImodView;

  /* 
   * Initialize data. 
   */
  plug->filename = NULL;
  if (firstTime) {
    nvals = ImodPrefs->getGenericSettings("BeadFixer", savedValues, 
                                          NUM_SAVED_VALS);
    loadSaved(plug->autoCenter, 2);
    loadSaved(plug->diameter, 3);
    loadSaved(plug->lightBead, 4);
    if (nvals > 7)
      plug->overlaySec = (int)(savedValues[5] + 0.01);
    loadSaved(plug->showMode, 7);
    loadSaved(plug->reverseOverlay, 8);
    loadSaved(plug->autoNewCont, 9);
  }

  /*
   * This creates the plug window.
   */
  plug->window  = new BeadFixer(imodDialogManager.parent(IMOD_DIALOG),
                                "bead fixer");

  imodDialogManager.add((QWidget *)plug->window, IMOD_DIALOG);

  // Get window position from settings the first time
  if (!firstTime) {
    plug->window->move(plug->left, plug->top);
  } else if (nvals >= 2) {
    plug->left = (int)savedValues[0];
    plug->top = (int)savedValues[1];
    diaLimitWindowPos(plug->window->width(), plug->window->height(), 
                      plug->left, plug->top);
    plug->window->move(plug->left, plug->top);
  }
  firstTime = 0;

  plug->window->show();
}

/* Execute the message in the strings.
   Keep the action definitions in imod_client_message.h */

int imodPlugExecuteMessage(ImodView *vw, QStringList *strings, int *arg)
{
  return plug->window->executeMessage(strings, arg);
}

int BeadFixer::executeMessage(QStringList *strings, int *arg)
{
  int mode;
  int action = (*strings)[*arg].toInt();

  // If window not open or no filename, need to just ignore this message
  if (action == MESSAGE_BEADFIX_REREAD && (!plug->window || !plug->filename))
    return 0;
  if (!plug->window)
    return 1;

  switch (action) {
  case MESSAGE_BEADFIX_OPENFILE:

    // The message is really to open if not open, so etomo can send it always
    // without reopening inappropriately
    if (plug->filename) {
      ++(*arg);
      return 0;
    }
    return plug->window->openFileByName((*strings)[++(*arg)].latin1());
  case MESSAGE_BEADFIX_REREAD:
    if (!plug->filename)
      return 0;
    return plug->window->reread();
  case MESSAGE_BEADFIX_SEEDMODE:
    plug->autoNewCont = (*strings)[++(*arg)].toInt();
    diaSetChecked(seedModeBox, plug->autoNewCont != 0);
    return 0;
  case MESSAGE_BEADFIX_AUTOCENTER:
    plug->autoCenter = (*strings)[++(*arg)].toInt();
    diaSetChecked(autoCenBox, plug->autoCenter != 0);
    return 0;
  case MESSAGE_BEADFIX_DIAMETER:
    plug->diameter = (*strings)[++(*arg)].toInt();
    diaSetSpinBox(diameterSpin, plug->diameter);
    return 0;
  case MESSAGE_BEADFIX_OPERATION:
    mode = (*strings)[++(*arg)].toInt();
    mode = B3DMIN(2, B3DMAX(0, mode));
    diaSetGroup(modeGroup, mode);
    modeSelected(mode);
    return 0;
  }
  return 1;
}

/* Open a tiltalign log file to find points with big residuals */

void BeadFixer::openFile()
{
  QString qname;
  char *filter[] = {"Align log files (align*.log)", "Log files (*.log)"};

  qname  = diaOpenFileName(this, "Select Tiltalign log file", 2, filter);
  
  if (qname.isEmpty())
    return;
  openFileByName(qname.latin1());
}
 
/* Open the log file with the given name, returning 1 if error */
 
int BeadFixer::openFileByName(const char *filename)
{
  if (plug->filename != NULL)
    free(plug->filename);
  plug->filename = strdup(filename);
  
  if (reread())
    return 1;

  rereadBut->setEnabled(true);    
  runAlignBut->setEnabled(true);
  return 0;
}


/* Read or reread the tiltalign log file whose name was already obtained */
/* Return -1 if there is an error opening file, or 1 if there is a memory error
   and the window will close */
int BeadFixer::reread()
{
  char line[MAXLINE];
  char *arealine;
  int newstyle, oldstyle = 0;
  int found = 0;
  int gotHeader = 0;
  int inpt, i;
  int numToSee = 0;
  FILE   *fp;
  Iobj *xobj = ivwGetExtraObject(plug->view);
  ResidPt *rpt;

  // Initialize extra object
  ivwClearExtraObject(plug->view);
  imodObjectSetColor(xobj, 1., 0., 0.);
  imodObjectSetValue(xobj, IobjFlagClosed, 0);

  if (plug->filename == NULL) 
    return -1;

  fp = fopen(plug->filename, "r");
  if(fp == NULL) {
    wprint("\aError opening file!\n");
    return -1;
  }

  wprint("Reading log file...");
         
  mNumAreas = 0;
  mNumResid = 0;
  mCurrentRes = -1;
  mIndlook = -1;

  // Outer loop searching for lines at top of residuals
  while (fgets(line, MAXLINE, fp) != NULL) {
    newstyle = strstr(line,"   #     #     #      X         Y        X")
      != NULL;
    if (!newstyle)
      oldstyle = strstr(line,"   #     #      X         Y        X")
        != NULL;
    if (newstyle || oldstyle) {
      mObjcont = newstyle;
      gotHeader = 1;

      // Allocate area data now
      if (mNumAreas >= mAreaMax) {
        if (mAreaMax)
          mAreaList = (AreaData *)realloc
            (mAreaList, (mAreaMax + 10) * sizeof(AreaData));
        else
          mAreaList = (AreaData *)malloc(10 * sizeof(AreaData));
        mAreaMax += 10;
        if (!mAreaList) {
          wprint("\aMemory error in bead fixer!\n");
          fclose(fp);
          close();
          return 1;
        }
      }
      
      // Set up global area 
      if (!mNumAreas) {
        mAreaList[0].areaX = 0;
        mAreaList[0].areaY = 0;
        mAreaList[0].firstPt = 0;
        mAreaList[0].numPts = 0;
        mNumAreas = 1;
      }

      // Next, loop on residual entries until a short (blank) line
      while (fgets(line, MAXLINE, fp) != NULL) {
        if (strlen(line) < 3)
          break;

        // Allocate residual memory
        if (mNumResid >= mResidMax) {
          if (mResidMax)
            mResidList = (ResidPt *)realloc
              (mResidList, (mResidMax + 100) * sizeof(ResidPt));
          else
            mResidList = (ResidPt *)malloc(100 * sizeof(ResidPt));
          mResidMax += 100;
        }

        if (!mResidList) {
          wprint("\aMemory error in bead fixer!");
          fclose(fp);
          close();
          return 1;
        }

        // Read and store data
        rpt = &(mResidList[mNumResid++]);
        if (mObjcont)
          sscanf(line, "%d %d %d %f %f %f %f %f", 
                 &rpt->obj, &rpt->cont, &rpt->view, &rpt->xcen, &rpt->ycen, 
                 &rpt->xres, &rpt->yres, &rpt->sd);
        else {
          sscanf(line, "%d %d %f %f %f %f %f", 
                 &inpt, &rpt->view, &rpt->xcen, &rpt->ycen, 
                 &rpt->xres, &rpt->yres, &rpt->sd);
          rpt->obj = 1;
          rpt->cont = inpt;
        }
        rpt->lookedAt = 0;
        mAreaList[mNumAreas - 1].numPts++;
        rpt->area = mNumAreas - 1;

        // If examine once is on, see if point is on looked list or new list
        if (mLookonce) {
          found = 0;
          for (i = 0; i < mNumLooked && !found; i++)
            if (rpt->obj == mLookedList[i].obj && 
                rpt->cont == mLookedList[i].cont &&
                rpt->view == mLookedList[i].view)
              found = 1;
          for (i = 0; i < mNumResid - 1 && !found; i++)
            if (rpt->obj == mResidList[i].obj && 
                rpt->cont == mResidList[i].cont &&
                rpt->view == mResidList[i].view)
              found = 1;
          if (!found)
            numToSee++;
        }

      }

      // Now look for another local area
      found = 0;
      while (!found && fgets(line, MAXLINE, fp) != NULL) {
        arealine = strstr(line,"Doing local area");
        if (arealine) {
          arealine[22]=0x00;
          found = 1;
          
          sscanf(&arealine[16], "%d %d", &mAreaList[mNumAreas].areaX, 
                 &mAreaList[mNumAreas].areaY);
          mAreaList[mNumAreas].numPts = 0;
          mAreaList[mNumAreas++].firstPt = mNumResid;
        }
      }

      // If none found, this breaks the top loop scanning for residual top line
      if (!found)
        break;
    }
  }
  fclose(fp);

  setCurArea(0);
  nextResBut->setEnabled(mNumResid);
  backUpBut->setEnabled(false);    
  if (!gotHeader)
    wprint("\aResidual data not found\n");
  else if (!mLookonce)
    wprint(" %d total residuals.\n", mNumResid);
  else
    wprint(" %d total residuals, %d to examine.\n", mNumResid, numToSee);
  return 0;
}

// Set current area, manage next local set button enabling and text
void BeadFixer::setCurArea(int area)
{
  mCurArea = area;
  nextLocalBut->setEnabled(mCurArea < mNumAreas - 1);
  nextLocalBut->setText(mCurArea ? 
                        "Go to Next Local Set" : "Go to First Local Set");
  moveAllBut->setEnabled(mCurArea > 0);
}

/* Jump to the next point with a big residual */

void BeadFixer::nextRes()
{
  int inobj, incont, inpt, inview, curpt, obj, nobj, cont, ncont, ipt, npnt;
  int obsav, cosav, ptsav, i;
  int found = 0;
  float  xr, yr, resval, dx, dy;
  Iobj *ob;
  Icont *con;
  Ipoint *pts;
  Ipoint tpt;
  float headLen = 2.5;
  ResidPt *rpt;
  Imod *imod = ivwGetModel(plug->view);

  // Copy and reset the bell flag
  int bell = mBell;
  mBell = 0;

  ivwControlActive(plug->view, 0);

  mIndlook = -1;
  undoMoveBut->setEnabled(false);
  movePointBut->setEnabled(false);
  if (!mNumResid || mCurrentRes >= mNumResid)
    return;


  // Coming into here, currentRes points to the last residual if any
  do {
    mCurrentRes++;
    if (mCurrentRes >= mNumResid) {
      if (mMovingAll)
        wprint("Moved %d points\n", mNumAllMoved);
      wprint("\aNo more residuals!\n");
      nextResBut->setEnabled(false);
      nextLocalBut->setEnabled(false);
      return;
    }
    
    rpt = &(mResidList[mCurrentRes]);
    inobj = rpt->obj;
    incont = rpt->cont;
    inview = rpt->view;
    xr = rpt->xres;
    yr = rpt->yres;

    /* See if point is on list */
    found = 0;
    for (i = 0; i < mNumLooked && !found; i++)
      if (inobj == mLookedList[i].obj && 
          incont == mLookedList[i].cont
          && inview == mLookedList[i].view)
        found = 1;

    /* Continue with next point if looking once and this point was found
       on the list */
  } while (mLookonce && found);

  /* Add point to list if it wasn't found */
  if (!found) {
    if (mNumLooked >= mLookedMax) {
      if (mLookedMax)
        mLookedList = (LookedPt *)realloc
          (mLookedList, (mLookedMax + 100) * sizeof(LookedPt));
      else
        mLookedList = (LookedPt *)malloc(100 * sizeof(LookedPt));
      mLookedMax += 100;
    }

    if (!mLookedList) {
      wprint("\aMemory error in bead fixer!\n");
      close();
      return;
    }
    mLookedList[mNumLooked].obj = inobj;
    mLookedList[mNumLooked].cont = incont;
    mLookedList[mNumLooked++].view = inview;
  }

  // Adjust the area and issue message if changed; set bell unless suppressed
  if (rpt->area != mCurArea) {
    if (mMovingAll)
      wprint("Moved %d points\n", mNumAllMoved);
    mMovingAll = false;
    wprint("Entering local area %d  %d,  %d residuals\n",
           mAreaList[rpt->area].areaX,
           mAreaList[rpt->area].areaY, mAreaList[rpt->area].numPts);
    setCurArea(rpt->area);
    if (!bell)
      bell = 1;
  }

  rpt->lookedAt = 1;
  found = 0;
  curpt=0;
  nobj = imodGetMaxObject(imod); 
  imodGetIndex(imod, &obsav, &cosav, &ptsav);

  if (mObjcont) {

    /* New case of direct object-contour listing */
    if (inobj > nobj) {
      wprint("\aObject not found!\n");
      return;
    }
    obj = inobj - 1;
    cont = incont - 1;
    imodSetIndex(imod, obj, cont, 0);
    ob = imodObjectGet(imod);
    ncont = imodObjectGetMaxContour(ob);
    if (incont <= ncont) {
      found = 1;
      con = imodContourGet(imod);
      npnt = imodContourGetMaxPoint(con);
    }
  } else {

    /* Old case of "point #", need to count through valid contours */
    ob = imodObjectGetFirst(imod);

    for (obj=0; obj < nobj ; obj++) {
      ncont = imodObjectGetMaxContour(ob);
      con = imodContourGetFirst(imod);
      for (cont = 0; cont < ncont; cont++)  {
        npnt = imodContourGetMaxPoint(con);
        if (npnt > 1) curpt++;
        if(curpt == incont) {
          found = 1;
          break;
        }
        con = imodContourGetNext(imod);
      }
      if (found)
        break;
      ob = imodObjectGetNext(imod);
    }
  }

  if (!found || !con) {
    wprint("\aContour not found!\n");
    imodSetIndex(imod, obsav, cosav, ptsav);
    return;
  }
  pts = imodContourGetPoints(con);
  for (ipt = 0; ipt < npnt; ipt++) {
    if(floor((double)(pts[ipt].z + 1.5f)) == inview) {

      // Insist that point is close to where it should be
      dx = pts[ipt].x - rpt->xcen;
      dy = pts[ipt].y - rpt->ycen;
      if (dx * dx + dy * dy > 225.) {
        wprint("\aPoint is > 15 pixels from position in log file\n");
        imodSetIndex(imod, obsav, cosav, ptsav);
        return;
      }

      imodSetIndex(imod, obj, cont, ipt);
      resval = sqrt((double)(xr*xr + yr*yr));
      if (bell > 0)
        wprint("\aResidual =%6.2f (%5.1f,%5.1f),%5.2f SDs\n",
               resval, xr, yr, rpt->sd);
      else if (!mMovingAll)
        wprint("Residual =%6.2f (%5.1f,%5.1f),%5.2f SDs\n",
               resval, xr, yr, rpt->sd);

      mIndlook = mCurrentRes;
      mObjlook = obj;
      mContlook = cont;
      mPtlook = ipt;
      mCurmoved = 0;
      movePointBut->setEnabled(true);

      // Make an arrow in the extra object
      con = imodContourNew();
      if (con) {
        ivwClearExtraObject(plug->view);
        ob = ivwGetExtraObject(plug->view);
        tpt.x = rpt->xcen;
        tpt.y = rpt->ycen;
        tpt.z = pts[ipt].z;
        imodPointAppend(con, &tpt);
        tpt.x += xr;
        tpt.y += yr;
        imodPointAppend(con, &tpt);
        tpt.x -= 0.707 * (xr - yr) * headLen / resval;
        tpt.y -= 0.707 * (xr + yr) * headLen / resval;
        imodPointAppend(con, &tpt);
        tpt.x = rpt->xcen + xr;
        tpt.y = rpt->ycen + yr;
        imodPointAppend(con, &tpt);
        tpt.x -= 0.707 * (xr + yr) * headLen / resval;
        tpt.y -= 0.707 * (-xr + yr) * headLen / resval;
        imodPointAppend(con, &tpt);
        imodObjectAddContour(ob, con);
      }
      if (!mMovingAll)
        ivwRedraw(plug->view);

      backUpBut->setEnabled(mCurrentRes > 0);    
      return;
    }
  }
  wprint("\aPoint not found in contour!\n");
  imodSetIndex(imod, obsav, cosav, ptsav);
  return;
}

// Go to next local area by just setting the current point to before it
// Suppress bell since user selected action
void BeadFixer::nextLocal()
{
  if (mCurArea >= mNumAreas - 1)
    return;
  mCurrentRes = mAreaList[mCurArea + 1].firstPt - 1;
  mBell = -1;
  nextRes();
}
 
// Go back to last point
void BeadFixer::backUp()
{
  int i, areaX, areaY, newRes;
  ResidPt *rpt;
  newRes = -1;

  if (!mNumResid)
    return;

  // Find the previous residual (that was looked at, if lookonce is on)
  for (i = mCurrentRes - 1; i >= 0 && newRes < 0; i--)
    if (!mLookonce || mResidList[i].lookedAt)
      newRes = i;

  if (newRes < 0) {
    if (mLookonce) {
      wprint("\aThere is no previous residual.  Try turning off \"Examine "
             "points once\".\n");
    } else {
      wprint("\aThere is no previous residual.\n");
      backUpBut->setEnabled(false);
    }
    return;
  }

  // Take current point off the examined list to allow it to be seen again
  if (mCurrentRes < mNumResid) {
    rpt = &(mResidList[mCurrentRes]);
    for (i = 0; i < mNumLooked; i++)
      if (rpt->obj == mLookedList[i].obj && 
          rpt->cont == mLookedList[i].cont
          && rpt->view == mLookedList[i].view)
        mLookedList[i].obj = -1;
  }
        
  // Disable backup button if back to first
  if (!newRes)
    backUpBut->setEnabled(false);

  // Give message if moved between areas, set the bell flag
  rpt = &(mResidList[newRes]);
  if (rpt->area != mCurArea) {
    setCurArea(rpt->area);
    areaX = mAreaList[rpt->area].areaX;
    areaY = mAreaList[rpt->area].areaY;
    if (!areaX && !areaY)
      wprint("Backing up into global solution residuals.\n");
    else
      wprint("Backing up into local area %d %d.\n", areaX, areaY);
    mBell = 1;
  }

  // Point to one before desired residual
  // Turn off look once flag, set flag that there is a resid, and get residual
  mCurrentRes = newRes - 1;
  i = mLookonce;
  mLookonce = 0;
  nextResBut->setEnabled(true);
  nextRes();
  mLookonce = i;
}

void BeadFixer::onceToggled(bool state)
{
  mLookonce = state ? 1 : 0;
}

void BeadFixer::clearList()
{
  mNumLooked = 0;
}

void BeadFixer::movePoint()
{
  int obj, cont, pt;
  Ipoint *pts;
  Icont *con;
  ResidPt *rpt;
  Imod *imod = ivwGetModel(plug->view);
  ivwControlActive(plug->view, 0);
     
  if (!mNumResid || mCurmoved  || mObjlook < 0 || mIndlook < 0) 
    return;

  imodGetIndex(imod, &obj, &cont, &pt);
  if (obj != mObjlook || cont != mContlook || pt != mPtlook) {
    wprint("\aThe current point is not the same as the point with the "
           "last residual examined!\n");
    return;
  }

  /* move the point.  Use the original point coordinates as starting point */
  rpt = &(mResidList[mIndlook]);
  con = imodContourGet(imod);
  pts = imodContourGetPoints(con);
  plug->view->undo->pointShift();
  mOldpt = pts[pt];
  mOldpt.x = rpt->xcen;
  mOldpt.y = rpt->ycen;
  mNewpt = mOldpt;
  mNewpt.x += rpt->xres;
  mNewpt.y += rpt->yres;
  pts[pt] = mNewpt;
  plug->view->undo->finishUnit();
  mObjmoved = mObjlook;
  mContmoved = mContlook;
  mPtmoved = mPtlook;

  /* set flags and buttons */
  mCurmoved = 1;
  mDidmove = 1;
  movePointBut->setEnabled(false);
  undoMoveBut->setEnabled(true);
  
  if (!mMovingAll)
    ivwRedraw(plug->view);
}

void BeadFixer::undoMove()
{
  int obsav, cosav, ptsav;
  int nobj, ncont;
  Iobj *ob;
  Icont *con;
  Ipoint *pts;
  float dx, dy, distsq;
  Imod *imod = ivwGetModel(plug->view);
  ivwControlActive(plug->view, 0);
     
  if(!mNumResid || !mDidmove) 
    return;
  imodGetIndex(imod, &obsav, &cosav, &ptsav);

  nobj = imodGetMaxObject(imod); 

  if (mObjmoved < nobj) {
    imodSetIndex(imod, mObjmoved, mContmoved, 
                 mPtmoved);
    ob = imodObjectGet(imod);
    ncont = imodObjectGetMaxContour(ob);
    if (mContmoved < ncont) {
      con = imodContourGet(imod);
      pts = imodContourGetPoints(con);
      if (mPtmoved < imodContourGetMaxPoint(con)) {

        /* Check that point is within 10 pixels of where it was */
        dx = pts[mPtmoved].x - mNewpt.x;
        dy = pts[mPtmoved].y - mNewpt.y;
        distsq = dx * dx + dy * dy;
        if (distsq < 100. && pts[mPtmoved].z == mNewpt.z) {
          plug->view->undo->pointShift();
          pts[mPtmoved] = mOldpt;
          plug->view->undo->finishUnit();
          mDidmove = 0;
          mCurmoved = 0;
          undoMoveBut->setEnabled(false);
          movePointBut->setEnabled(true);
          ivwRedraw(plug->view);
          return;
        }    
      }
    }
  }
    
  wprint("\aMoved point no longer exists or is not close enough "
         "to where it was moved to!\n");
  imodSetIndex(imod, obsav, cosav, ptsav);
  undoMoveBut->setEnabled(false);
}

/*
 * Move all points in current area by residual
 */
void BeadFixer::moveAll()
{
  int startArea = mCurArea;
  if (mCurArea <= 0 || mCurrentRes >= mNumResid)
    return;
  mMovingAll = true;
  mNumAllMoved = 0;
  while (mCurArea == startArea && mCurrentRes < mNumResid) {
    if (mIndlook >= 0 && !mCurmoved) {
      movePoint();
      mNumAllMoved++;
    }
    mBell = -1;
    nextRes();
  }
  mMovingAll = false;
  ivwRedraw(plug->view);
}

int BeadFixer::foundgap(int obj, int cont, int ipt, int before)
{
  Imod *imod = ivwGetModel(plug->view);

  if(mLastob == obj && mLastco == cont && mLastpt == ipt
     && mLastbefore == before)
    return 1;

  mLastob = obj;
  mLastco = cont;
  mLastpt = ipt;
  mLastbefore = before;
  imodSetIndex(imod, obj, cont, ipt);
  makeUpDownArrow(before);
  ivwRedraw(plug->view);
  return 0;
}

void BeadFixer::makeUpDownArrow(int before)
{
  int size = 12;
  int idir = before ? -1 : 1;
  Iobj *xobj = ivwGetExtraObject(plug->view);
  Imod *imod = ivwGetModel(plug->view);
  Ipoint pt;
  Ipoint *curpt;
  Icont * con;

  ivwClearExtraObject(plug->view);

  // Initialize extra object
  imodObjectSetColor(xobj, 1., 1., 0.);
  imodObjectSetValue(xobj, IobjFlagClosed, 0);
  curpt = imodPointGet(imod);
  if (!curpt)
    return;
  pt = *curpt;
  pt.y += idir * size / 2;
  con = imodContourNew();
  if (con) {
    imodPointAppend(con, &pt);
    pt.y += idir * size;
    imodPointAppend(con, &pt);
    pt.x -= idir * size / 3;
    pt.y -= idir * size / 3;
    imodPointAppend(con, &pt);
    pt.x += idir * size / 3;
    pt.y += idir * size / 3;
    imodPointAppend(con, &pt);
    pt.x += idir * size / 3;
    pt.y -= idir * size / 3;
    imodPointAppend(con, &pt);
    imodObjectAddContour(xobj, con);
  }
}

/* Jump to next gap in the model, or place where it is not tracked to first
   or last section */

void BeadFixer::findGap(int idir)
{
  int  obj, nobj, cont, ncont, ipt, npnt;
  int obsav, cosav, ptsav, curob, curco, curpt, lookback;
  int iptmin, iptmax, iztst, ipt2, foundnext;
  float zcur, zmin, zmax;
  Iobj *ob;
  Icont *con;
  Ipoint *pts;
  int xsize, ysize, zsize;
  static int beforeVerbose = 1;

  Imod *imod = ivwGetModel(plug->view);
  ivwGetImageSize(plug->view, &xsize, &ysize, &zsize);

  /* This is needed to make button press behave just like hotkey in syncing
     the image */
  ivwControlActive(plug->view, 0);

  con = imodContourGet(imod);
  imodGetIndex(imod, &obsav, &cosav, &ptsav);

  curob = mLastob;
  curco = mLastco;
  curpt = mLastpt;
  lookback = 0;

  if(mIfdidgap == 0 || mLastob < 0 || mLastco < 0 || mLastpt < 0) {
    curob = curco = curpt = 0;
    mLastob = -1;
    mLastbefore = 0;
    lookback = 1;
  }

  mIfdidgap = 1;

  /* If last one was at start of track, go back to first point of contour */
  if (mLastbefore)
    curpt = 0;

  imodSetIndex(imod, curob, curco, curpt);
  nobj = imodGetMaxObject(imod); 

  ob = imodObjectGet(imod);
  con = imodContourGet(imod);

  for (obj=curob; obj < nobj && obj >= 0; obj += idir) {
    ncont = imodObjectGetMaxContour(ob);
    for (cont = curco; cont < ncont && cont >= 0; cont += idir)  {
      npnt = imodContourGetMaxPoint(con);
      if(npnt > 0) {
        pts = imodContourGetPoints(con);

        /* find min and max z in contour */
        zmin = pts->z;
        iptmin = 0;
        zmax = zmin;
        iptmax = 0;
        for (ipt = 0; ipt < npnt; ipt++) {
          if (zmin > pts[ipt].z) {
            zmin = pts[ipt].z;
            iptmin = ipt;
          }
          if (zmax < pts[ipt].z) {
            zmax = pts[ipt].z;
            iptmax = ipt;
          }
        }

        /* If looking back, check zmin, set it as gap before if not 0 */
        if(lookback == 1 && zmin > 0.5) {
          if(foundgap(obj,cont,iptmin, 1) == 0) {
            if (beforeVerbose)
              wprint("\aContour %d is missing points before current point.  "
                     "Use PageDown to get to view with missing point.\n",
                     cont+1);
            else
              wprint("\aContour %d is missing points before current point.\n",
                     cont+1);
            beforeVerbose = 0;
            return;
          }
        }

        /* from current point forward, check for existence of a point at 
           next z value; if none, it's a gap */
        for (ipt = curpt; ipt < npnt && ipt >= 0; ipt += idir) {
          if (ipt != iptmax) {
            zcur = pts[ipt].z;
            iztst = (int)(zcur + 1.5);
            foundnext = 0;
            for (ipt2 = 0; ipt2 < npnt; ipt2++) {
              if (iztst == (int)(pts[ipt2].z + 0.5)) {
                foundnext = 1;
                break;
              }
            }
            if (!foundnext)
              if(foundgap(obj, cont, ipt, 0) == 0) 
                return;
          }
        }

        /* If get to end of contour, check zmax against z of file */
        if (idir > 0) {
          if (zmax + 1.1f < zsize)
            if(foundgap(obj, cont, iptmax, 0) == 0) 
              return;
        } else if (zmin > 0.5) {
          if (foundgap(obj,cont,iptmin, 1) == 0) {
            wprint("\aContour %d is missing points before current point.\n",
                     cont+1);
            return;
          }
        }
      }
      if (idir > 0) {
        con = imodContourGetNext(imod);
        lookback = 1;
        curpt = 0;
      } else if (curco) {
        imodPrevContour(imod);
        con = imodContourGet(imod);
        curpt = imodContourGetMaxPoint(con) - 1;
      }
    }

    if (idir > 0) {
      ob = imodObjectGetNext(imod);
      con = imodContourGetFirst(imod);
      curco = 0;
    } else if (curob) {
      imodPrevObject(imod);
      ob = imodObjectGet(imod);
      curco = imodObjectGetMaxContour(ob) - 1;
      imodSetIndex(imod, curob - 1, curco, -1);
      con = imodContourGet(imod);
    }
  }
  if (idir > 0)
    wprint("\aNo more gaps found!\n");
  else
    wprint("\aNo gaps found back to beginning of model.\n");

  imodSetIndex(imod, obsav, cosav, ptsav);
  return;
}

void BeadFixer::resetStart()
{
  mIfdidgap = 0;
}

void BeadFixer::resetCurrent()
{
  int ob, co, pt;
  Imod *imod = ivwGetModel(plug->view);
  imodGetIndex(imod, &ob, &co, &pt);
  if (pt < 0)
    return;
  mLastob = ob;
  mLastco = co;
  mLastpt = pt;
}

void BeadFixer::reattach()
{
  Imod *imod = ivwGetModel(plug->view);
  if (mLastob < 0 || mLastco < 0 || mLastpt < 0)
    return;
  imodSetIndex(imod, mLastob, mLastco, mLastpt);
  ivwRedraw(plug->view);
}

/* 
 * Insert a point: if autocentering, find nearest bead.  If in seed mode,
 * make a new contour unless this is an apparent continuation point
 */
int BeadFixer::insertPoint(float imx, float imy)
{
  Imod *imod = ivwGetModel(plug->view);
  Icont *cont;
  Iobj *obj;
  Ipoint *pts;
  Ipoint newPt;
  int  curx, cury, curz, index, ob, i,npnt;
  double zdiff, dist;
  int xsize, ysize, zsize;


  // Skip if in residual mode: so in seed or gap mode, it will handle insertion
  // and at least keep the contour in order
  if (plug->showMode == RES_MODE)
    return 0;
  ivwGetLocation(plug->view, &curx, &cury, &curz);
  ivwGetImageSize(plug->view, &xsize, &ysize, &zsize);

  // Autocenter the point if selected, error and say handled if fail
  if (plug->autoCenter && findCenter(imx, imy, curz)) {
    wprint("\aAutocentering failed to find a point\n");
    return 1;
  }

  // Do not start new contours in gap mode
  if (plug->showMode == GAP_MODE && !imodContourGet(imod)) {
    wprint("\aNo automatic new contours in gap filling mode.\nUse \"Reattach"
           " to Point at Gap\" first to fill the current gap.\n");
    return 1;
  }

  obj = imodObjectGet(imod);
  cont = ivwGetOrMakeContour(plug->view, obj, 0);
  if (!cont) {
    wprint("\aFailed to get contour to add point to\n");
    return 1;
  }
  npnt = imodContourGetMaxPoint(cont);
  pts = imodContourGetPoints(cont);
  newPt.x = imx;
  newPt.y = imy;
  newPt.z = curz;
    
  // Search current contour for closest point below the current Z and set the
  // insertion point after it
  index = 0;
  zdiff = 1000000;
  for (i = 0; i < npnt; i++) {
    if (pts[i].z < curz && curz - pts[i].z < zdiff) {
      zdiff = curz - pts[i].z;
      index = i + 1;
    }
  }

  // But in seed mode, see if need to start a new contour - i.e. if there is
  // a point at the same z or the point at nearest Z is farther away than
  // a criterion
  if (plug->autoNewCont && plug->showMode == SEED_MODE) {
    zdiff = 1000000;
    for (i = 0; i < npnt; i++) {
      if (fabs((double)(curz - pts[i].z)) < zdiff) {
        zdiff = fabs((double)(curz - pts[i].z));
        dist = imodPointDistance(&pts[i], &newPt);
      }
    }
    if (zdiff < 0.5 || dist > 2. * plug->diameter) {
      imodGetIndex(imod, &ob, &i, &npnt);
      imodSetIndex(imod, ob, -1, -1);
      cont = ivwGetOrMakeContour(plug->view, obj, 0);
      if (!cont) {
        wprint("\aFailed to get contour to add point to\n");
        return 1;
      }
      index = 0;
    }
  }

  ivwRegisterInsertPoint(plug->view, cont, &newPt, index);

  // See if the arrow should be moved
  if (plug->showMode == GAP_MODE) {

    // If looking before and still not at Z = 0
    if (mLastbefore && curz)
      makeUpDownArrow(mLastbefore);
    else if (curz && curz <  zsize - 1) {

      // Otherwise need to look through points and see if next one exists
      pts = imodContourGetPoints(cont);
      npnt = imodContourGetMaxPoint(cont);
      index = 0;
      for (i = 0; i < npnt; i++) {
        if (curz + 1 == (int)(pts[i].z + 0.5)) {
          index = 1;
          break;
        }
      }
      if (!index)
        makeUpDownArrow(0);
    }
  }
  
  ivwDraw(plug->view, IMOD_DRAW_MOD | IMOD_DRAW_NOSYNC);
  return 1;
}

/*
 * Move the current point using the autocentering function
 */
int BeadFixer::modifyPoint(float imx, float imy)
{
  Imod *imod = ivwGetModel(plug->view);
  int  curx, cury, curz;
  Icont *cont;
  Ipoint *pts;
  int ob, co, pt;

  ivwGetLocation(plug->view, &curx, &cury, &curz);
  imodGetIndex(imod, &ob, &co, &pt);
  if (pt < 0)
    return 0;
  cont = imodContourGet(imod);
  pts = imodContourGetPoints(cont);
  if ((int)floor(pts[pt].z + 0.5) != curz)
    return 0;
  if (findCenter(imx, imy, curz))
    return 0;
  plug->view->undo->pointShift();
  pts[pt].x = imx;
  pts[pt].y = imy;
  plug->view->undo->finishUnit();
  ivwDraw(plug->view, IMOD_DRAW_MOD | IMOD_DRAW_NOSYNC);
  return 1;
}

/*
 * Find a nearby point with a large bead integral on the current section
 * and return the modified coordinates
 */
int BeadFixer::findCenter(float &imx, float &imy, int curz)
{
  ImodView *vw = plug->view;
  float edgeWidth = 1.5f;
  float buffer = 1.5f;
  float fartherCrit = 2.f;
  int ipolar = plug->lightBead ? 1 : -1;

  int xcen = (int)floor(imx + 0.5);
  int ycen = (int)floor(imy + 0.5);
  float radius = plug->diameter / 2.;
  int search = (int)B3DMAX(6, B3DMIN(MAX_DIAMETER, 3 * radius));

  float sumrad = radius + buffer;
  int look = (int)(2. * (sumrad + edgeWidth) + 2.);
  float radCrit = sumrad * sumrad;
  float edgeCrit = (sumrad + edgeWidth) * (sumrad + edgeWidth);
  double xsum, ysum, wsum, wsum2, innerCrit, outerCrit, ringx, ringy;
  double grandx, grandy, grandMax, ringMax, radsq;
  int numRing, ring, x, y, ix, iy, nedge, xsize, ysize, zsize;
  float pixval, edge,sum;
  int xstart, xend, ystart, yend;

  ivwGetImageSize(plug->view, &xsize, &ysize, &zsize);

  // Search in radius-sized rings from the selected point; in each ring
  // find the point with the largest centroid
  grandMax = -1.e30;
  numRing = (int)(search / radius + 1.);
  for (ring = 0; ring < numRing; ring++) {
    innerCrit = ring * radius * ring * radius;
    outerCrit = (ring + 1) * radius * (ring + 1) * radius;
    ringMax = -1.e30;
    for (x = xcen - search; x <= xcen + search; x++) {
      if (x < 0 || x >= xsize)
        continue;
      for (y = ycen - search; y <= ycen + search; y++){
        radsq = (x -xcen) * (x -xcen) + (y - ycen) * (y - ycen);
        if (radsq < innerCrit || radsq >= outerCrit || y < 0 || y >= ysize)
          continue;

        // Get edge
        sum = 0.;
        nedge = 0;
        ystart = B3DMAX(0, y - look);
        yend = B3DMIN(ysize - 1, y + look); 
        xstart = B3DMAX(0, x - look);
        xend = B3DMIN(xsize - 1, x + look); 
                        
        for (iy = ystart; iy <= yend; iy++) {
          for (ix = xstart; ix <= xend; ix++) {
            radsq = (ix + 0.5 - x) * (ix + 0.5 - x) + (iy + 0.5 - y) +
              (iy + 0.5 - y);
            if (radsq > radCrit && radsq <= edgeCrit) {
              nedge++;
              sum += ivwGetValue(vw, ix, iy, curz);
            }
          }
        }
        edge = sum / nedge;
        //imodPrintStderr("At %d %d  edge %.1f  %d pix", x, y, edge, nedge);
        
        // Get integral above edge mean and CG
        nedge = 0;
        wsum = xsum = ysum = wsum2 = 0.;
        for (iy = ystart; iy <= yend; iy++) {
          for (ix = xstart; ix <= xend; ix++) {
            radsq = (ix + 0.5 - x) * (ix + 0.5 - x) + (iy + 0.5 - y) *
              (iy + 0.5 - y);
            if (radsq <= radCrit) {
              pixval = ivwGetValue(vw, ix, iy, curz) - edge;
              wsum2 += pixval;
              if (ipolar * pixval > 0.) {
                xsum += ix * pixval;
                ysum += iy * pixval;
                wsum += pixval;
                nedge++;
              }
            }
          }
        }

        //imodPrintStderr("wsum2 %.0f  x %.2f  y %.2f  %d pix\n"
        //                , wsum2, xsum / wsum, ysum/wsum, nedge);
        // Find max in the ring
        if (wsum != 0. && wsum2 * ipolar > ringMax) {
          ringMax = wsum2 * ipolar;
          ringx = xsum/wsum + 0.5;
          ringy = ysum/wsum + 0.5;
        }
      }
    }
    //imodPrintStderr("ring %d max %f at %f,%f\n", ring, ringMax, ringx,ringy);

    // If the max is sufficiently larger than the previous one, or it is
    // larger and within one radius, take it as a new max
    if (ringMax > fartherCrit * grandMax || 
        (ringMax > grandMax && (ringx - grandx) * (ringx - grandx) + 
         (ringy - grandy) * (ringy - grandy) < radius * radius)) {
      grandx = ringx;
      grandy = ringy;
      grandMax = ringMax;
    }
  }
  //imodPrintStderr("grand max %f at %f,%f\n",  grandMax, grandx, grandy);
  if (grandMax < -1.e29)
    return 1;
  imx = (float)grandx;
  imy = (float)grandy;
  return 0;
}

// Slots for centering/seed controls
void BeadFixer::seedToggled(bool state)
{
  plug->autoNewCont = state;
}

void BeadFixer::autoCenToggled(bool state)
{
  plug->autoCenter = state ? 1 : 0;
}

void BeadFixer::lightToggled(bool state)
{
  plug->lightBead = state ? 1 : 0;
  setOverlay(plug->overlayOn, plug->overlayOn);
}

void BeadFixer::diameterChanged(int value)
{
  setFocus();
  plug->diameter = value;
}

void BeadFixer::overlayToggled(bool state)
{
  overlaySpin->setEnabled(state);
  plug->overlayOn = state ? 1: 0;
  setOverlay(1, plug->overlayOn);
}

void BeadFixer::overlayChanged(int value)
{
  setFocus();
  plug->overlaySec = value;
  setOverlay(plug->overlayOn, plug->overlayOn);
}

void BeadFixer::reverseToggled(bool state)
{
  plug->reverseOverlay = state ? 1 : 0;
  setOverlay(plug->overlayOn, plug->overlayOn);
}

// set the overlay mode to given state if the doIt flag is set
void BeadFixer::setOverlay(int doIt, int state)
{
  if (doIt)
    ivwSetOverlayMode(plug->view, state ? plug->overlaySec : 0, 
                      plug->reverseOverlay,
                      (plug->lightBead + plug->reverseOverlay) % 2);
}

void BeadFixer::modeSelected(int value)
{
  // Manage seed mode items
  showWidget(seedModeBox, value == SEED_MODE);
  showWidget(overlayHbox, value == SEED_MODE);
  showWidget(reverseBox, value == SEED_MODE);

  // Manage gap filling items
  showWidget(nextGapBut, value == GAP_MODE);
  showWidget(prevGapBut, value == GAP_MODE);
  showWidget(reattachBut, value == GAP_MODE);
  showWidget(resetStartBut, value == GAP_MODE);
  showWidget(resetCurrentBut, value == GAP_MODE);

  // Manage autocenter items
  showWidget(cenLightHbox, value != RES_MODE);
  showWidget(diameterHbox, value != RES_MODE);
    
  // Manage residual mode items
  showWidget(openFileBut, value == RES_MODE);
  showWidget(runAlignBut, value == RES_MODE);
  showWidget(rereadBut, value == RES_MODE);
  showWidget(nextLocalBut, value == RES_MODE);
  showWidget(nextResBut, value == RES_MODE);
  showWidget(movePointBut, value == RES_MODE);
  showWidget(undoMoveBut, value == RES_MODE);
  showWidget(moveAllBut, value == RES_MODE);
  showWidget(backUpBut, value == RES_MODE);
  showWidget(clearListBut, value == RES_MODE);
  showWidget(examineBox, value == RES_MODE);

  adjustSize();

  // Turn overlay mode on or off if needed
  if ((value == SEED_MODE || plug->showMode == SEED_MODE) && plug->overlayOn)
    setOverlay(1, value == SEED_MODE ? 1 : 0);
  plug->showMode = value;
}

void BeadFixer::showWidget(QWidget *widget, bool state)
{
  if (!widget)
    return;
  if (state)
    widget->show();
  else
    widget->hide();
}

 
// THE WINDOW CLASS CONSTRUCTOR
 
static char *buttonLabels[] = {"Done", "Help"};
static char *buttonTips[] = {"Close Bead Fixer", "Open help window"};

BeadFixer::BeadFixer(QWidget *parent, const char *name)
  : DialogFrame(parent, 2, 1, buttonLabels, buttonTips, true, 
                ImodPrefs->getRoundedStyle(), "Bead Fixer", "", name)
{
  QPushButton *button;
  QCheckBox *box;
  QString qstr;
  overlayHbox = NULL;
  reverseBox = NULL;
  mRunningAlign = false;
  mTopTimerID = 0;
  mStayOnTop = false;
  mIfdidgap = 0;
  mLastob = -1;
  mCurmoved = 0;
  mObjlook = -1;
  mIndlook = -1;
  mDidmove = 0;
  mLookonce = 1;
  mNumResid = 0;
  mResidMax = 0;
  mLookedMax = 0;
  mNumLooked = 0;
  mCurArea = -1;
  mNumAreas = 0;
  mAreaList = NULL;
  mAreaMax = 0;
  mCurrentRes = -1;
  mBell = 0;
  mMovingAll = false;
  mRoundedStyle = ImodPrefs->getRoundedStyle();

  mLayout->setSpacing(4);
  topBox = new QHBox(this);
  mLayout->addWidget(topBox);
  topBox->setSpacing(6);

  QToolButton *toolBut = new QToolButton(topBox);
  toolBut->setToggleButton(true);
  QIconSet iconSet;
  iconSet.setPixmap(QPixmap((const char **)pegged), QIconSet::Automatic, 
                    QIconSet::Normal, QIconSet::On);
  iconSet.setPixmap(QPixmap((const char **)unpegged), QIconSet::Automatic,
                    QIconSet::Normal, QIconSet::Off);
  toolBut->setIconSet(iconSet);
  toolBut->setOn(false);
  QSize hint = toolBut->sizeHint();
  toolBut->setFixedWidth(hint.width());
  toolBut->setFixedHeight(hint.height());
  connect(toolBut, SIGNAL(toggled(bool)), this, SLOT(keepOnTop(bool)));
  QToolTip::add(toolBut, "Keep bead fixer window on top");

  modeGroup = new QVButtonGroup("Operation", topBox, "mode group");
  connect(modeGroup, SIGNAL(clicked(int)), this, SLOT(modeSelected(int)));
  modeGroup->setInsideSpacing(0);
  modeGroup->setInsideMargin(5);

  QRadioButton *radio = diaRadioButton("Make seed", modeGroup);
  QToolTip::add(radio, "Show tools for making seed model");
  radio = diaRadioButton("Fill gaps", modeGroup);
  QToolTip::add(radio, "Show tools for finding and filling gaps");
  radio = diaRadioButton("Fix big residuals", modeGroup);
  QToolTip::add(radio, "Show tools for fixing big residuals");
  diaSetGroup(modeGroup, plug->showMode);

  cenLightHbox = new QHBox(this);
  mLayout->addWidget(cenLightHbox);
  topBox->setSpacing(8);
    //box = diaCheckBox("Autocenter", this, mLayout);
  autoCenBox = new QCheckBox("Autocenter", cenLightHbox);
  autoCenBox->setFocusPolicy(QWidget::NoFocus);
  connect(autoCenBox, SIGNAL(toggled(bool)), this, SLOT(autoCenToggled(bool)));
  diaSetChecked(autoCenBox, plug->autoCenter != 0);
  QToolTip::add(autoCenBox, 
                "Automatically center inserted point on nearby bead");

  // box = diaCheckBox("Light beads", this, mLayout);
  box = new QCheckBox("Light", cenLightHbox);
  box->setFocusPolicy(QWidget::NoFocus);
  connect(box, SIGNAL(toggled(bool)), this, SLOT(lightToggled(bool)));
  diaSetChecked(box, plug->lightBead != 0);
  QToolTip::add(box, "Beads are lighter not darker than background");

  diameterHbox = new QHBox(this);
  mLayout->addWidget(diameterHbox);
  QLabel *label = new QLabel("Diameter", diameterHbox);
  diameterSpin = new QSpinBox(1, MAX_DIAMETER, 1, diameterHbox);
  diameterSpin->setFocusPolicy(QWidget::ClickFocus);
  QObject::connect(diameterSpin, SIGNAL(valueChanged(int)), this,
                   SLOT(diameterChanged(int)));
  QToolTip::add(diameterSpin, "Diameter of beads in pixels");
  diaSetSpinBox(diameterSpin, plug->diameter);

  seedModeBox = diaCheckBox("Automatic new contour", this, mLayout);
  connect(seedModeBox, SIGNAL(toggled(bool)), this, SLOT(seedToggled(bool)));
  diaSetChecked(seedModeBox, plug->autoNewCont != 0);
  QToolTip::add(seedModeBox, "Make new contour for every point in a new "
                "position");
  

  if (App->rgba && !App->cvi->rawImageStore) {
    overlayHbox = new QHBox(this);
    mLayout->addWidget(overlayHbox);
    overlayBox = new QCheckBox("Overlay - view", overlayHbox);
    overlayBox->setFocusPolicy(QWidget::NoFocus);
    connect(overlayBox, SIGNAL(toggled(bool)), this, 
            SLOT(overlayToggled(bool)));
    QToolTip::add(overlayBox, "Show another section in color overlay -"
                  " Hot key: /");

    overlaySpin = new QSpinBox(-MAX_OVERLAY, MAX_OVERLAY, 1, overlayHbox);
    overlaySpin->setFocusPolicy(QWidget::ClickFocus);
    QObject::connect(overlaySpin, SIGNAL(valueChanged(int)), this,
                     SLOT(overlayChanged(int)));
    QToolTip::add(overlaySpin, "Interval to overlay section");
    diaSetSpinBox(overlaySpin, plug->overlaySec);
    overlaySpin->setEnabled(plug->overlayOn != 0);

    reverseBox = diaCheckBox("Reverse overlay contrast", this, mLayout);
    connect(reverseBox, SIGNAL(toggled(bool)), this, 
            SLOT(reverseToggled(bool)));
    QToolTip::add(reverseBox, "Show color overlay in reverse contrast");
    diaSetChecked(reverseBox, plug->reverseOverlay != 0);
  }    

  nextGapBut = diaPushButton("Go to Next Gap", this, mLayout);
  connect(nextGapBut, SIGNAL(clicked()), this, SLOT(nextGap()));
  QToolTip::add(nextGapBut, "Go to gap in model - Hot key: spacebar");

  prevGapBut = diaPushButton("Go to Previous Gap", this, mLayout);
  connect(prevGapBut, SIGNAL(clicked()), this, SLOT(prevGap()));
  QToolTip::add(prevGapBut, "Go back to previous gap in model");

  reattachBut = diaPushButton("Reattach to Gap Point", this, mLayout);
  connect(reattachBut, SIGNAL(clicked()), this, SLOT(reattach()));
  QToolTip::add(reattachBut, "Make point at current gap be the current point"
                " again");

  resetStartBut = diaPushButton("Start from Beginning", this, mLayout);
  connect(resetStartBut, SIGNAL(clicked()), this, SLOT(resetStart()));
  QToolTip::add(resetStartBut, "Look for gaps from beginning of model");

  resetCurrentBut = diaPushButton("Start from Current Point", this, mLayout);
  connect(resetCurrentBut, SIGNAL(clicked()), this, SLOT(resetCurrent()));
  QToolTip::add(resetCurrentBut, "Look for gaps from current point");

  openFileBut = diaPushButton("Open Tiltalign Log File", this, mLayout);
  connect(openFileBut, SIGNAL(clicked()), this, SLOT(openFile()));
  QToolTip::add(openFileBut, "Select an alignment log file to open");

  runAlignBut = diaPushButton("Save && Run Tiltalign", this, mLayout);
  connect(runAlignBut, SIGNAL(clicked()), this, SLOT(runAlign()));
  runAlignBut->setEnabled(false);
  QToolTip::add(runAlignBut, "Save model and run Tiltalign");

  rereadBut = diaPushButton("Reread Log File", this, mLayout);
  connect(rereadBut, SIGNAL(clicked()), this, SLOT(rereadFile()));
  rereadBut->setEnabled(false);
  QToolTip::add(rereadBut, "Read the previously specified file again");

  nextLocalBut = diaPushButton("Go to First Local Set", this, mLayout);
  connect(nextLocalBut, SIGNAL(clicked()), this, SLOT(nextLocal()));
  nextLocalBut->setEnabled(false);
  QToolTip::add(nextLocalBut, "Skip to residuals in next local area");

  nextResBut = diaPushButton("Go to Next Big Residual", this, mLayout);
  connect(nextResBut, SIGNAL(clicked()), this, SLOT(nextRes()));
  nextResBut->setEnabled(false);
  QToolTip::add(nextResBut, "Show next highest residual - Hot key: "
                "apostrophe");

  movePointBut = diaPushButton("Move Point by Residual", this, mLayout);
  connect(movePointBut, SIGNAL(clicked()), this, SLOT(movePoint()));
  movePointBut->setEnabled(false);
  QToolTip::add(movePointBut, "Move point to position that fits alignment"
                " solution - Hot key: semicolon");

  undoMoveBut = diaPushButton("Undo Move", this, mLayout);
  connect(undoMoveBut, SIGNAL(clicked()), this, SLOT(undoMove()));
  undoMoveBut->setEnabled(false);
  QToolTip::add(undoMoveBut, 
                "Move point back to previous position - Hot key: U");

  moveAllBut = diaPushButton("Move All in Local Area", this, mLayout);
  connect(moveAllBut, SIGNAL(clicked()), this, SLOT(moveAll()));
  moveAllBut->setEnabled(false);
  QToolTip::add(moveAllBut, "Move all points in current area by residual"
                " - Hot key: colon");

  backUpBut = diaPushButton("Back Up to Last Point", this, mLayout);
  connect(backUpBut, SIGNAL(clicked()), this, SLOT(backUp()));
  backUpBut->setEnabled(false);
  QToolTip::add(backUpBut, "Back up to last point examined - "
                "Hot key: double quote");

  examineBox = diaCheckBox("Examine Points Once", this, mLayout);
  connect(examineBox, SIGNAL(toggled(bool)), this, SLOT(onceToggled(bool)));
  diaSetChecked(examineBox, mLookonce != 0);
  QToolTip::add(examineBox, "Skip over points examined before");

  clearListBut = diaPushButton("Clear Examined List", this, mLayout);
  connect(clearListBut, SIGNAL(clicked()), this, SLOT(clearList()));
  QToolTip::add(clearListBut, "Allow all points to be examined again");

  connect(this, SIGNAL(actionClicked(int)), this, SLOT(buttonPressed(int)));
  modeSelected(plug->showMode);
}

void BeadFixer::buttonPressed(int which)
{
  if (!which)
    close();
  else
    imodShowHelpPage("beadfix.html");
}

// Change to flag to keep on top or run timer as for info window
void BeadFixer::keepOnTop(bool state)
{
#ifdef STAY_ON_TOP_HACK
  mStayOnTop = state;

  // Start or kill the timer
  if (state)
    mTopTimerID = startTimer(200);
  else if (mTopTimerID) {
    killTimer(mTopTimerID);
    mTopTimerID = 0;
  }

#else
  int flags = getWFlags();
  if (state)
    flags |= WStyle_StaysOnTop;
  else
    flags ^= WStyle_StaysOnTop;
  QPoint p(geometry().x(), geometry().y());
  // Using pos() jumps on Windows
  // Also, pos() jumps up-left on Unix, geometry() jumps down-right
  // Unless we access the pos !
  QPoint p2 = pos();
  reparent(0, flags, p, true);  
#endif
}

// Timer event to keep window on top in Linux, or watch for tiltalign done
void BeadFixer::timerEvent(QTimerEvent *e)
{
  if (mStayOnTop)
    raise();
}

// Routine to run tiltalign: it needs to start the thread to make the
// system call, start a timer to watch results, and disable buttons
void BeadFixer::runAlign()
{
  if (mRunningAlign || !plug->filename)
    return;

  inputSaveModel(plug->view);

  QString comStr, fileStr, vmsStr;
  int dotPos;
  char *imodDir = getenv("IMOD_DIR");
  char *cshell = getenv("IMOD_CSHELL");
  if (!imodDir) {
    wprint("\aCannot run tiltalign; IMOD_DIR not defined.\n");
    return;
  }
  if (!cshell)
    cshell = "tcsh";
  fileStr = plug->filename;
  
  // Remove the leading path and the extension
  dotPos = fileStr.findRev('/');
  if (dotPos >= 0)
    fileStr = fileStr.right(fileStr.length() - dotPos - 1);
  dotPos = fileStr.findRev('.');
  if (dotPos > 0)
    fileStr.truncate(dotPos);

  // 7/3/06: The old way was to run vmstocsh and pipe to tcsh in a "system"
  // command inside a thread - but in Windows it hung with -L listening to 
  // stdin.  This way worked through "system" call but QProcess is cleaner
  vmsStr = QString(imodDir) + "/bin/submfg -q";
  comStr.sprintf("%s -f %s %s", cshell, 
                 (QDir::convertSeparators(vmsStr)).latin1(), fileStr.latin1());

  mAlignProcess = new QProcess(QStringList::split(" ", comStr));
  connect(mAlignProcess, SIGNAL(processExited()), this, SLOT(alignExited()));
  if (!mAlignProcess->start()) {
    wprint("\aError trying to start tiltalign process.\n");
    return;
  }

  mRunningAlign = true;
  rereadBut->setEnabled(false);
  openFileBut->setEnabled(false);
  runAlignBut->setEnabled(false);
  nextResBut->setEnabled(false);
  nextLocalBut->setEnabled(false);
}

// When align exits, check the status and reenable buttons
void BeadFixer::alignExited()
{
  int err;

  // Check if exit staus, clean up and reenable buttons
  if (!mAlignProcess->normalExit())
    wprint("\aAbnormal exit trying to run tiltalign.\n");
  else if ((err = mAlignProcess->exitStatus()))
    wprint("\aError (return code %d) running tiltalign.\n", err);

  delete mAlignProcess;
  mRunningAlign = false;

  if (reread() <= 0) {
    rereadBut->setEnabled(true);
    runAlignBut->setEnabled(true);
    openFileBut->setEnabled(true);
  }
}

// The window is closing, remove from manager
void BeadFixer::closeEvent ( QCloseEvent * e )
{
  double posValues[NUM_SAVED_VALS];

  // Delete the process object to disconnect
  if (mRunningAlign)
    delete mAlignProcess;
  mRunningAlign = false;

  // Get geometry and save in settings and in structure for next time
  QRect pos = ivwRestorableGeometry(plug->window);
  posValues[0] = pos.left();
  posValues[1] = pos.top();
  plug->top = pos.top();
  plug->left = pos.left();
  posValues[2] = plug->autoCenter;
  posValues[3] = plug->diameter;
  posValues[4] = plug->lightBead;
  posValues[5] = plug->overlaySec;
  posValues[6] = 0;    // Was up down arrow flag
  posValues[7] = plug->showMode;
  posValues[8] = plug->reverseOverlay;
  posValues[9] = plug->autoNewCont;
  
  ImodPrefs->saveGenericSettings("BeadFixer", NUM_SAVED_VALS, posValues);

  imodDialogManager.remove((QWidget *)plug->window);
  ivwClearExtraObject(plug->view);

  setOverlay((plug->showMode == SEED_MODE && plug->overlayOn) ? 1 : 0, 0);
  plug->overlayOn = 0;

  if (mTopTimerID)
    killTimer(mTopTimerID);
  mTopTimerID = 0;

  plug->view = NULL;
  plug->window = NULL;
  if (mLookedMax && mLookedList)
    free(mLookedList);
  mLookedMax = 0;
  if (plug->filename)
    free(plug->filename);
  plug->filename = NULL;
  if (mAreaList && mAreaMax)
    free(mAreaList);
  mAreaMax = 0;
  if (mResidList && mResidMax)
    free(mResidList);
  mResidMax = 0;
  
  e->accept();
}

// Set widths of buttons and top box
void BeadFixer::setFontDependentWidths()
{
  int width2 = diaGetButtonWidth(this, mRoundedStyle, 1.15,
                                 "Move Point by Residual");
  int width = diaGetButtonWidth(this, mRoundedStyle, 1.15,
                                "Open Tiltalign Log File");
  if (width < width2)
    width = width2;
  topBox->setFixedWidth(width);
  diameterHbox->setFixedWidth(width);
  if (overlayHbox)
    overlayHbox->setFixedWidth(width);
  resetStartBut->setFixedWidth(width);
  resetCurrentBut->setFixedWidth(width);
  openFileBut->setFixedWidth(width);
  runAlignBut->setFixedWidth(width);
  rereadBut->setFixedWidth(width);
  nextLocalBut->setFixedWidth(width);
  nextResBut->setFixedWidth(width);
  movePointBut->setFixedWidth(width);
  undoMoveBut->setFixedWidth(width);
  backUpBut->setFixedWidth(width);
  moveAllBut->setFixedWidth(width);
  clearListBut->setFixedWidth(width);
  reattachBut->setFixedWidth(width);
}

void BeadFixer::fontChange( const QFont & oldFont )
{
  mRoundedStyle = ImodPrefs->getRoundedStyle();
  setFontDependentWidths();
  DialogFrame::fontChange(oldFont);
}

// Close on escape, pass on keys
void BeadFixer::keyPressEvent ( QKeyEvent * e )
{
  if (e->key() == Qt::Key_Escape)
    close();
  else
    ivwControlKey(0, e);
}

void BeadFixer::keyReleaseEvent ( QKeyEvent * e )
{
  ivwControlKey(1, e);
}

/*
    $Log$
    Revision 1.39  2006/09/06 22:14:45  mast
    Really make it ignore the reopne message regardless of state

    Revision 1.38  2006/08/24 16:53:34  mast
    Reopen message should not generate error if align log not open yet

    Revision 1.37  2006/07/18 04:17:30  mast
    Removed show up down checkbox, made it not sync image position on mouse
    actions, enabled keys only in correct mode, required points to be
    within 15 pixels of position in log file

    Revision 1.36  2006/07/05 04:18:26  mast
    Added reverse contrast in overlay and reattach in gap mode

    Revision 1.35  2006/07/04 17:24:19  mast
    Added output of number of residuals to examine

    Revision 1.34  2006/07/04 03:50:56  mast
    Switched running align from a thread to a QProcess

    Revision 1.33  2006/07/03 23:28:11  mast
    Converted system call to call tcsh submfg to fix windows hang with -L mode

    Revision 1.32  2006/07/03 21:16:49  mast
    Fixed saving of show mode and made it move arrow up as well as down

    Revision 1.31  2006/07/03 04:13:53  mast
    Added seed overlay mode, set up 3 mode radio button and morphing of
    window depending on modes, made gap finding keep track of position and
    be able to go backwards

    Revision 1.30  2006/07/01 00:42:06  mast
    Changed message to open a log file only if one not open yet

    Revision 1.29  2006/03/01 19:13:06  mast
    Moved window size/position routines from xzap to dia_qtutils

    Revision 1.28  2006/03/01 18:20:51  mast
    Made fixing all in local area stop displaying image and listing residuals

    Revision 1.27  2006/02/13 05:16:06  mast
    Added mouse processing, autocentering and seed mode

    Revision 1.26  2005/06/13 16:39:52  mast
    Clarified message when points are missing before current point.

    Revision 1.25  2005/06/13 16:24:50  mast
    Added rounded style argument when constructing window

    Revision 1.24  2005/04/13 19:12:26  mast
    fixed tooltip

    Revision 1.23  2005/04/12 18:57:47  mast
    Added move all in local area, improved some button enabling

    Revision 1.22  2005/02/19 01:29:50  mast
    Removed function to clear extra object

    Revision 1.21  2004/12/22 22:22:05  mast
    Fixed bug in reading "old" log files

    Revision 1.20  2004/11/20 05:05:27  mast
    Changes for undo/redo capability

    Revision 1.19  2004/11/04 23:30:55  mast
    Changes for rounded button style

    Revision 1.18  2004/09/24 17:58:01  mast
    Added ability to execute messages for opening/rereading file

    Revision 1.17  2004/07/09 21:26:55  mast
    Strip directory path off when running align, to avoid spaces in path

    Revision 1.16  2004/06/25 20:05:40  mast
    Based the move by residual on residual data instead of current point value,
    and rewrote to make most plug variables be class members

    Revision 1.15  2004/06/24 15:34:15  mast
    Rewrote to read in all data to internal structures at once, and made it
    move between areas automatically, improved backup logic

    Revision 1.14  2004/06/23 04:12:32  mast
    Stupid change just before checking in

    Revision 1.13  2004/06/23 03:32:19  mast
    Changed to save and restore window position

    Revision 1.12  2004/06/20 22:43:15  mast
    Fixed problem that made no residuals be found.

    Revision 1.11  2004/06/12 15:13:03  mast
    Needed some new Qt includes

    Revision 1.10  2004/06/12 00:58:11  mast
    Switched to reading in whole file at once

    Revision 1.9  2004/05/11 14:17:53  mast
    Needed to put an enable of the run align button inside conditional

    Revision 1.8  2004/05/07 22:14:53  mast
    Switched to a variable other than QT_THREAD_SUPPORT for the run align button

    Revision 1.7  2004/05/04 17:52:32  mast
    Forgot to put AlignThread::run inside ifdef.

    Revision 1.6  2004/05/03 19:32:20  mast
    had to decalre exit code as int

    Revision 1.5  2004/05/03 19:17:43  mast
    Added ability to run tiltalign if there is thread support

    Revision 1.4  2004/04/29 00:28:40  mast
    Added button to keep window on top

    Revision 1.3  2004/03/30 18:56:26  mast
    Added hot key for next local set

    Revision 1.2  2004/01/22 19:12:43  mast
    changed from pressed() to clicked() or accomodated change to actionClicked

    Revision 1.1  2003/10/01 05:09:36  mast
    Conversion to internal module in 3dmod

    Revision 3.9  2003/08/01 00:16:51  mast
    Made "examine once" be default and rearranged buttons

    Revision 3.8  2003/07/07 21:32:49  mast
    Fix stupid malloc/realloc problem in pointer list

    Revision 3.7  2003/06/29 14:34:41  mast
    Fix problem of multiple vector displays

    Revision 3.6  2003/06/29 14:23:20  mast
    Added ability to back up to previous residual

    Revision 3.5  2003/06/27 20:25:11  mast
    Implemented display of residual vectors in extra object

    Revision 3.4  2003/05/29 05:03:43  mast
    Make filter for align*.log only

    Revision 3.3  2003/05/12 19:13:39  mast
    Add hot key summary and fix spelling

*/
