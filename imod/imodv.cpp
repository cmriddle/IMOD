/*  IMOD VERSION 2.50
 *
 *  imodv.cpp -- The main imodv entry point for standalone or imod operation
 *
 *  Original author: James Kremer
 *  Revised by: David Mastronarde   email: mast@colorado.edu
 */

/*****************************************************************************
 *   Copyright (C) 1995-2001 by Boulder Laboratory for 3-Dimensional Fine    *
 *   Structure ("BL3DFS") and the Regents of the University of Colorado.     *
 *                                                                           *
 *   BL3DFS reserves the exclusive rights of preparing derivative works,     *
 *   distributing copies for sale, lease or lending and displaying this      *
 *   software and documentation.                                             *
 *   Users may reproduce the software and documentation as long as the       *
 *   copyright notice and other notices are preserved.                       *
 *   Neither the software nor the documentation may be distributed for       *
 *   profit, either in original form or in derivative works.                 *
 *                                                                           *
 *   THIS SOFTWARE AND/OR DOCUMENTATION IS PROVIDED WITH NO WARRANTY,        *
 *   EXPRESS OR IMPLIED, INCLUDING, WITHOUT LIMITATION, WARRANTY OF          *
 *   MERCHANTABILITY AND WARRANTY OF FITNESS FOR A PARTICULAR PURPOSE.       *
 *                                                                           *
 *   This work is supported by NIH biotechnology grant #RR00592,             *
 *   for the Boulder Laboratory for 3-Dimensional Fine Structure.            *
 *   University of Colorado, MCDB Box 347, Boulder, CO 80309                 *
 *****************************************************************************/

/*  $Author$

$Date$

$Revision$
Log at end of file
*/

#include <stdio.h>
#include <stdlib.h>
#include <limits.h>
#include <math.h>
#include "imodv_window.h"
#include <qapplication.h>
#include <qdir.h>
#include <qimage.h>
#include <qpixmap.h>
#include "dia_qtutils.h"

#include "imodv.h"
#include "imodv_views.h"
#include "imod.h"
#include "imod_object_edit.h"
#include "imod_display.h"
#include "imodv_gfx.h"
#include "imodv_stereo.h"
#include "imodv_modeled.h"
#include "preferences.h"

#include "b3dicon.xpm"

// static declarations
static void usage(char *pname);
static int load_models(int n, char **fname, ImodvApp *a);
static int openWindow(ImodvApp *a);
static int getVisuals(ImodvApp *a);
static void initstruct(ImodView *vw, ImodvApp *a);
static int imodv_init(ImodvApp *a, struct Mod_Draw *md);


// GLOBAL VARIABLES for the imodv and draw structures
ImodvApp  ImodvStruct;
struct Mod_Draw Imodv_mdraw;
ImodvApp *Imodv = &ImodvStruct;

/* A global to indicate if the window is closed */
int ImodvClosed = 1;


#define DEFAULT_XSIZE  512
#define DEFAULT_YSIZE  512


/* the default graphics rendering attributes for OpenGL */
static ImodGLRequest True24 = {1, 1, 24, 1, 1};
static ImodGLRequest True15 = {1, 1, 15, 1, 1};
static ImodGLRequest True12 = {1, 1, 12, 1, 1};
static ImodGLRequest True24nodep = {1, 1, 24, 0, 1};
static ImodGLRequest True15nodep = {1, 1, 15, 0, 1};
static ImodGLRequest True12nodep = {1, 1, 12, 0, 1};

/* List the attribute lists in order of priority */
static ImodGLRequest *OpenGLAttribList[] = {
  &True24, &True15, &True12, &True24nodep, &True15nodep, &True12nodep, 
  NULL
};

/*void __eprintf(void){return;} */

static void usage(char *pname)
{
  imodVersion(pname);
  imodCopyright();
  fprintf(stderr, "options: all Qt options plus:\n");
  fprintf(stderr, "\t-f                Open window to max size.\n");
  fprintf(stderr, "\t-b color_name     Background color for rendering.\n");
  fprintf(stderr, "\t-s width,height   Window size in pixels.\n");
  fprintf(stderr, "\t-D                Debug mode.\n");
  fprintf(stderr, "\t-h                Print this help message.\n");
  exit(-1);
}

static char *blackString = "black";

/* DEFAULT INITIALIZATION OF THE STRUCTURES
   9/2/02: also called for model view initialization in imod */
static int imodv_init(ImodvApp *a, struct Mod_Draw *md)
{
  a->nm = 0;
  a->cm = 0;
  a->mod = NULL;
  a->imod = NULL;
  a->mat  = imodMatNew(3);
  a->rmat  = imodMatNew(3);
  a->dobj = imodObjectNew();
  a->obj = a->dobj;
  a->ob = 0;
  a->md = md;
  a->cnear = 0;
  a->cfar = 1000;
  a->fovy = 0;
  a->dlist = 0;
  a->update_dlist = 1;
  a->movie = 0;
  a->movieFrames = 0;
  a->wpid = 0;
  a->stereo = IMODV_STEREO_OFF;
  a->plax = 5.0f;
  a->lightx = a->lighty = 0;
  a->winx = DEFAULT_XSIZE;
  a->winy = DEFAULT_YSIZE;
  a->rbgname = blackString;
  a->rbgcolor = new QColor(0, 0, 0);

  md->xorg = md->yorg = md->zorg = 0;
  md->xrot = md->yrot = md->zrot = 0;  /* current rotation. */
  md->zoom = 1.0f;
  md->arot = 10;
  md->atrans = 5.0f;
  md->azoom = 1.05f;
  md->azscale = 0.2;
  md->xrotm = 0; /* rotation movie */
  md->yrotm = 0;
  md->zrotm = 0;

  /* control flags */
  a->current_subset = 0;
  a->crosset    = 0;
  a->fullscreen = 0;
  a->drawall    = 0;
  a->moveall    = 1;
  a->alpha      = 0;
  imodViewDefault(&a->view);
  a->view.cnear = 0.0f;
  a->view.cfar  = 1.0f;
  a->doPick = 0;
  a->wPick = a->hPick = 5;

  a->lighting  = 1;
  a->depthcue  = 0;
  a->wireframe = 0;
  a->lowres = 0;
  a->SGIStereoCommand = NULL;
  a->SGIRestoreCommand = NULL;
  return(0);
}

// ADDITIONAL INITIALIZATION TO OPEN MODEL VIEW FROM IMOD
/* DNM 9/2/02: changed to call imodv_init for bulk of the initialization */
static void initstruct(ImodView *vw, ImodvApp *a)
{
  Ipoint imageMax;

  imodv_init(a, &Imodv_mdraw);

  a->nm = 1;
  a->mod = (Imod **)malloc(sizeof(Imod *));
  a->mod[0] = vw->imod;
  a->imod = vw->imod;

  /* DNM 8/3/01: start with current object if defined */
  if (a->imod->cindex.object >= 0 && 
      a->imod->cindex.object < a->imod->objsize) {
    a->ob = a->imod->cindex.object;
    a->obj = &(a->imod->obj[a->ob]);
  }

  /* control flags */
  a->fullscreen = 0;

  a->standalone = 0;
  a->texMap  = 0;
  a->texTrans = 0;
  a->vi = vw;

  a->SGIStereoCommand  = ImodRes_SGIStereoCommand();
  a->SGIRestoreCommand = ImodRes_SGIRestoreCommand();

  // Recompute scale and shift of operating view and current view - replaces
  // old method of setting up views
  imageMax.x = vw->xsize;
  imageMax.y = vw->ysize;
  imageMax.z = vw->zsize;
  imodViewDefaultScale(a->imod, a->imod->view, &imageMax);
  imodViewDefaultScale(a->imod, &a->imod->view[a->imod->cview], &imageMax);
  return;
}


// GET THE VISUAL INFORMATION NEEDED TO OPEN GL WIDGETS
/* DMN 9/2/02: replaced old imodvSetCmap with this, which is accessed from
   both imodv and ximodv (model view startup in imod), and which gets the
   best visuals for double and single buffering then does the old work of
   imodvSetCmap to get color map if needed in color index mode */

static int getVisuals(ImodvApp *a)
{
  int i, colorDB, colorSB;
  int depthSB = -1;
  int depthDB = -1;
  ImodGLVisual *visual;

  // Keep track of what depth request is used to get the visuals selected
  // and of the actual depth bits that result
  a->enableDepthSB = -1;
  a->enableDepthDB = -1;

  // Loop through all requests, asking first for double buffer then for single
  // buffer.  When one is found, the depth is set
  for (i = 0; OpenGLAttribList[i] != NULL; i++) {
    if (depthDB < 0) {
      visual = imodFindGLVisual(*OpenGLAttribList[i]);
      if (visual) {
        a->enableDepthDB = visual->depthEnabled;
	a->stereoDB = visual->stereo;
        depthDB = visual->depthBits;
        colorDB = visual->colorBits;
      }
    }
      
    if (depthSB < 0) {
      OpenGLAttribList[i]->doubleBuffer = 0;
      visual = imodFindGLVisual(*OpenGLAttribList[i]);
      if (visual) {
        a->enableDepthSB = visual->depthEnabled;
	a->stereoSB = visual->stereo;
        depthSB = visual->depthBits;
        colorSB = visual->colorBits;
      }
    }

    /* If got both, stop the loop */
    if (depthDB >= 0 && depthSB >= 0)
      break;
  }

  /* error if no visuals */
  if (depthDB < 0 && depthSB < 0)
    return 1;

  if (!depthDB || !depthSB)
    fprintf(stderr, "3dmodv warning: using a visual with"
            " no depth buffer\n");

  if (depthDB < 0)
    fprintf(stderr, "3dmodv warning: no double buffer visual available.\n");
  else if (Imod_debug)
    printf("DB visual: %d color bits, %d depth bits, stereo %d\n",
	   colorDB, depthDB, a->stereoDB);
  if (depthSB < 0)
    fprintf(stderr, "3dmodv warning: no single buffer visual available.\n");
  else if (Imod_debug)
    printf("SB visual: %d color bits, %d depth bits, stereo %d\n",
	   colorSB, depthSB, a->stereoSB);

  // set to double buffer if visual exists
  a->db = depthDB >= 0 ? 1 : 0;
  return 0;
}

// OPEN THE MAIN WINDOW
static int openWindow(ImodvApp *a)
{
  int deskWidth = QApplication::desktop()->width();
  int deskHeight = QApplication::desktop()->height();
  int newWidth = a->winx;
  int needy = a->winy;

  a->lighting = Imodv->imod->view->world & VIEW_WORLD_LIGHT;
  a->lowres = Imodv->imod->view->world & VIEW_WORLD_LOWRES;
  a->mainWin = new ImodvWindow(a->standalone, a->enableDepthDB, 
                               a->enableDepthSB, a->lighting, a->lowres);

  if (!a->mainWin)
    return 1;

  ImodvClosed = 0;
  imodvSetCaption();
  a->mainWin->setIcon(*(a->iconPixmap));

  // If the user did not enter a window size, just resize to that before
  // showing
  if (a->winx == DEFAULT_XSIZE && a->winy == DEFAULT_YSIZE) {
    a->mainWin->resize(a->winx, a->winy);
    if (a->fullscreen)
      a->mainWin->showMaximized();
    a->mainWin->show();
  } else {

    // Otherwise, show the window and then set the size and fix the position
    // This seems not to cause two initial draws, but it might someday
    a->mainWin->show();
    int newHeight = needy + a->mainWin->height() - a->winy;
    if (newHeight > deskHeight - 50)
      newHeight = deskHeight - 50;
    if (newWidth > deskWidth - 30)
      newWidth = deskWidth -30;

    QPoint pos = a->mainWin->pos();
    int xleft = pos.x();
    int ytop = pos.y();
    if (xleft + newWidth > deskWidth - 16)
      xleft = deskWidth - 16 - newWidth;
    if (ytop + newHeight > deskHeight - 40)
      ytop = deskHeight - 40 - newHeight;

    if (Imod_debug)
      fprintf(stderr, "Sizes: imodv %d %d, GL %d %d: "
            "resize %d %d\n", a->mainWin->width(), a->mainWin->height(),
             a->mainWin->mCurGLw->width(), 
            a->mainWin->mCurGLw->height(),
            newWidth, newHeight);
    a->mainWin->setGeometry(xleft, ytop, newWidth, newHeight);
  }

  return(0);
}

// OPEN THE MODELS IN IMODV
static int load_models(int n, char **fname, ImodvApp *a)
{
  int i, ob, co;

  if (n < 1)
    return(0);
  a->mod = (Imod **)malloc(sizeof(Imod *) * n);
  a->nm = n;
  a->cm = 0;
  for(i = 0; i < n; i++){
    a->mod[i] = imodRead((char *)(QDir::convertSeparators(QString(fname[i]))).
      latin1());
    if (!a->mod[i]){
      fprintf(stderr, "Error loading %s\n", fname[i]);
      return(-1);
    }

    /* DNM 6/20/01: find out max time and set current time */
    a->mod[i]->tmax = 0;
    for (ob = 0; ob < a->mod[i]->objsize; ob++)
      for (co = 0; co < a->mod[i]->obj[ob].contsize; co++)
        if (a->mod[i]->tmax < a->mod[i]->obj[ob].cont[co].type)
          a->mod[i]->tmax = a->mod[i]->obj[ob].cont[co].type;
    a->mod[i]->ctime = a->mod[i]->tmax ? 1 : 0;

    /* DNM: changes for storage of object properties in view and 
       relying on default scaling.  Also, make sure every model has
       the view to use set up 
       6/26/03: switch to new method, just initialize views in each model */

    imodvViewsInitialize(a->mod[i]);
  }

  a->imod = (a->mod[a->cm]);
  /* DNM 8/3/01: start with current object if defined */
  if (a->imod->cindex.object >= 0 && 
      a->imod->cindex.object < a->imod->objsize) {
    a->ob = a->imod->cindex.object;
    a->obj = &(a->imod->obj[a->ob]);
  }
   
  return(0);
}

// THE ENTRY POINT FOR STANDALONE IMODV
int imodv_main(int argc, char **argv)
{
  int i;
  ImodvApp *a = Imodv;
  a->standalone = 1;
  imodv_init(a, &Imodv_mdraw);

  // DNM 5/17/03: The Qt application and preferences are already gotten

  // Parse options
  for (i = 1; i < argc; i++){
    if (argv[i][0] == '-'){
      switch (argv[i][1]){

      case 'b':
	a->rbgname = strdup(argv[++i]);
	break;

      case 'D':
	Imod_debug = 1;
	break;

      case 'f':
	a->fullscreen = 1;
	break;

      case 's':
	sscanf(argv[++i], "%d%*c%d", &a->winx, &a->winy);
	break;

      case 'h':
        usage(argv[0]);
        exit(1);
        break;

      default:
        if (strcmp("-modv", argv[i]) && 
            strcmp("-view", argv[i])) {
          fprintf(stderr, "3dmodv error: illegal option %s\n", 
                  argv[i]);
          exit(1);
        }

      }
    } else
      break;
  }

  a->db        = 1;

  // Make a color from the named color; fallback to black
  QString qstr = a->rbgname;
  a->rbgcolor->setNamedColor(a->rbgname);
  if (!a->rbgcolor->isValid())
    a->rbgcolor->setRgb(0, 0, 0);

  if (getVisuals(a) != 0) {
    fprintf(stderr, "3dmodv error: Couldn't get rendering visual.\n");
    exit(-1);
  }

  if (argc - i < 1)
    usage(argv[0]);

  /* DNM 1/29/03: already forked in imod, no need to do it here */
  
  if (load_models(argc - i, &(argv[i]), Imodv))
    exit(-1);

  a->iconPixmap = new QPixmap(QImage(b3dicon));

  openWindow(Imodv);

  diaSetTitle("3dmodv");

  return qApp->exec();
}


// THE CALL TO OPEN THE MODEL VIEW WINDOW FROM IMOD
void imodv_open()
{
  ImodView *vw = App->cvi;
  Imodv = &ImodvStruct;
  ImodvStruct.md = &Imodv_mdraw;
  ImodvApp *a = Imodv;

  int ob, co;
  Imod *imod = vw->imod;

  /* mt model ? */
  if (!imod){
    wprint("Model View didn't open because "
           "there is no model loaded.\n");
    return;
  }

  /* DNM 6/26/03: eliminate test for 2 points, now that it can use image size
     to set scaling */

  /* check for already open? */
  if (!ImodvClosed){
    a->mainWin->raise();
    return;
  }

  initstruct(vw, a);
  a->iconPixmap = App->iconPixmap;

  if (getVisuals(a) != 0) {
    wprint("Couldn't get rendering visual for model view."
           "  Try running 3dmodv separately.\n");
    imodMatDelete(a->mat);
    imodMatDelete(a->rmat);
    return;
  }

  if (openWindow(a)) {
    wprint("Failed to open model view window.\n");
    return;
  }
    
}

// TO CLOSE FROM IMOD
void imodv_close()
{
  if (!ImodvClosed)
    Imodv->mainWin->close();
}

// TO DRAW THE MODEL FROM IMOD
void imodv_draw()
{
  if (!ImodvClosed)
    imodvDraw(Imodv);
  return;
}

/* DNM: a routine for imod to notify imodv of a change in model */
void imodv_new_model(Imod *mod)
{
  Ipoint imageMax;

  if (ImodvClosed)
    return;

  Imodv->imod = mod;
  Imodv->mod[0] = mod;

  // Recompute scale and shift of operating view and current view - replaces
  // earlier method of setting up views
  imageMax.x = App->cvi->xsize;
  imageMax.y = App->cvi->ysize;
  imageMax.z = App->cvi->zsize;
  imodViewDefaultScale(mod, mod->view, &imageMax);
  imodViewDefaultScale(mod, &mod->view[mod->cview], &imageMax);

  imodvSelectModel(Imodv, 0);
}

void imodvSetCaption()
{
  ImodvApp *a = Imodv;
  char *window_name;
  QString str;
  if (ImodvClosed)
    return;

  window_name = imodwEithername((char *)(a->standalone ? "3dmodv:" : 
                                 "3dmod Model View: "), a->imod->fileName, 1);
  if (window_name) {
    str = window_name;
    free(window_name);
  } 
  if (str.isEmpty())
    str = "3dmod Model View";

  a->mainWin->setCaption(str);
}

// To call imodDraw if not in standalone mode
void imodvDrawImodImages()
{
  if (Imodv->standalone)
    return;
  imodDraw(App->cvi, IMOD_DRAW_MOD);
  imod_object_edit_draw();
}

/*
$Log$
Revision 4.8  2003/05/18 22:58:33  mast
simplify creating icon pixmap

Revision 4.7  2003/05/18 22:06:37  mast
Changed to start QApplication before calling, and to create icon pixmap

Revision 4.6  2003/04/25 03:28:32  mast
Changes for name change to 3dmod

Revision 4.5  2003/04/17 21:48:44  mast
simplify -imodv option processing

Revision 4.4  2003/03/26 23:22:00  mast
Set up to use preferences

Revision 4.3  2003/03/04 21:41:05  mast
Added function for refreshing imod windows from imodv

Revision 4.2  2003/02/27 17:42:38  mast
Remove include of unistd for windows

Revision 4.1  2003/02/10 20:29:00  mast
autox.cpp

Revision 1.1.2.11  2003/01/29 17:50:58  mast
Fork now happens before imodv_main is called

Revision 1.1.2.10  2003/01/29 01:29:42  mast
add call for imod to close imodv

Revision 1.1.2.9  2003/01/27 00:30:07  mast
Pure Qt version and general cleanup

Revision 1.1.2.8  2003/01/01 19:12:31  mast
changes to start Qt application in standalone mode

Revision 1.1.2.7  2003/01/01 05:46:29  mast
changes for qt version of stereo

Revision 1.1.2.6  2002/12/18 04:15:14  mast
new includes for imodv modules

Revision 1.1.2.5  2002/12/17 22:28:20  mast
cleanup of unused variables and SGI errors

Revision 1.1.2.4  2002/12/17 18:42:22  mast
Qt version, incorporating ximodv startup code

Revision 1.1.2.3  2002/12/14 05:41:08  mast
Got qxt startup in the right place

Revision 1.1.2.2  2002/12/06 21:58:40  mast
*** empty log message ***

Revision 1.1.2.1  2002/12/05 16:28:37  mast
Open a qxt application

Revision 3.5  2002/12/01 15:34:41  mast
Changes to get clean compilation with g++

Revision 3.4  2002/11/30 06:07:22  mast
Corrected tables after addition of true-15 visual

Revision 3.3  2002/11/27 03:29:45  mast
Made it look for both single and double buffer visuals as long as they
are both RGB or both color index.  Added a true 15 visual, better than
pseudo 12 (present on O2).

Revision 3.2  2002/09/04 00:24:48  mast
Added CVS header.  Changed to getting visuals then passing them to GLw.

*/
