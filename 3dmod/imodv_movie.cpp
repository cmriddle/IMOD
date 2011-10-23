/*  imodv_movie.c -- Movie creation dialog for imodv.
 *
 *  Original Original author: James Kremer
 *  Revised by: David Mastronarde   email: mast@colorado.edu
 *
 *  Copyright (C) 1995-2004 by Boulder Laboratory for 3-Dimensional Electron
 *  Microscopy of Cells ("BL3DEMC") and the Regents of the University of 
 *  Colorado.  See dist/COPYRIGHT for full copyright notice.
 *
 *  $Id$
 */

#include <qapplication.h>
#include "formv_movie.h"
#include "dia_qtutils.h"
#include "imodv.h"
#include "imod.h"
#include "imodview.h"
#include "b3dgfx.h"
#include "imodv_gfx.h"
#include "imodv_input.h"
#include "imodv_image.h"
#include "imodv_movie.h"
#include "imodv_window.h"
#include "imodv_modeled.h"
#include "preferences.h"
#include "control.h"
#include "scalebar.h"

/* The movie control structure  */
struct imodvMovieDialogStruct
{
  imodvMovieForm *dia;
  ImodvApp  *a;
  int saved;
  int reverse;
  int longway;
  int montage;
  int file_format;
  int fullaxis;
  int abort;
  int frames;
  int montFrames;
  int overlap;
};

/* The resident structure and pointer to it */
static struct imodvMovieDialogStruct movieStruct = 
  {NULL, NULL, 0,0,0,0,0,0,0,0,0,0};
static struct imodvMovieDialogStruct *movie = &movieStruct;

/* Local functions */
static void imodvMakeMontage(int frames, int overlap);
static void imodvMakeMovie(int frames);
static void setstep(int index, int frame, int loLim, int hiLim, float *start,
                    float *step);


void imodvMovieHelp()
{
  imodShowHelpPage("modelMovie.html#TOP");
}

static void xinput(void)
{
  QApplication::flush();
  qApp->processEvents();
}

// Set the starting values to the current display values
void imodvMovieSetStart()
{
  Iview *vw = &Imodv->imod->view[0];
  movie->fullaxis = 0;

  movie->dia->setStart(0, vw->rot.x);
  movie->dia->setStart(1, vw->rot.y);
  movie->dia->setStart(2, vw->rot.z);
  movie->dia->setStart(3, vw->trans.x);
  movie->dia->setStart(4, vw->trans.y);
  movie->dia->setStart(5, vw->trans.z);
  movie->dia->setStart(6, vw->rad);
  if (!Imodv->standalone) {
    movie->dia->setStart(7, (int)(Imodv->vi->xmouse + 1.5));
    movie->dia->setStart(8, (int)(Imodv->vi->ymouse + 1.5));
    movie->dia->setStart(9, (int)(Imodv->vi->zmouse + 1.5));
    movie->dia->setStart(10, imodvImageGetTransparency());
    movie->dia->setStart(11, imodvImageGetThickness());
  }
}

// Set the ending values to the current display values
void imodvMovieSetEnd()
{
  Iview *vw = &Imodv->imod->view[0];
  movie->fullaxis = 0;

  movie->dia->setEnd(0, vw->rot.x);
  movie->dia->setEnd(1, vw->rot.y);
  movie->dia->setEnd(2, vw->rot.z);
  movie->dia->setEnd(3, vw->trans.x);
  movie->dia->setEnd(4, vw->trans.y);
  movie->dia->setEnd(5, vw->trans.z);
  movie->dia->setEnd(6, vw->rad);
  if (!Imodv->standalone) {
    movie->dia->setEnd(7, (int)(Imodv->vi->xmouse + 1.5));
    movie->dia->setEnd(8, (int)(Imodv->vi->ymouse + 1.5));
    movie->dia->setEnd(9, (int)(Imodv->vi->zmouse + 1.5));
    movie->dia->setEnd(10, imodvImageGetTransparency());
    movie->dia->setEnd(11, imodvImageGetThickness());
  }
}

// Do full axis rotation: set start and end both to same values
void imodvMovieFullAxis(int ixy)
{
  imodvMovieSetStart();
  imodvMovieSetEnd();
  movie->fullaxis = ixy;
}

// The dialog say it wants to close, so send it close signal
void imodvMovieQuit()
{
  movie->dia->close();
}

// When the dialog actually closes, get button states, clean up and stop movie
void imodvMovieClosing()
{
  movie->dia->getButtonStates(movie->longway, movie->reverse, movie->montage,
                              movie->file_format, movie->saved);
  movie->dia->getFrameBoxes(movie->frames, movie->montFrames);
  imodvDialogManager.remove((QWidget *)movie->dia);
  movie->dia = NULL;
  movie->abort = 1;
}

void imodvMovieStop()
{
  movie->abort = 1;
}

void imodvMovieMake()
{
  movie->dia->getButtonStates(movie->longway, movie->reverse, movie->montage,
                              movie->file_format, movie->saved);
  movie->dia->getFrameBoxes(movie->frames, movie->montFrames);

  /* DNM: only make if not already making */
  if (movie->abort) {
    if (movie->montage)
      imodvMakeMontage(movie->montFrames, movie->overlap);
    else
      imodvMakeMovie(movie->frames);
  }
}

void imodvMovieDialog(ImodvApp *a, int state)
{
  QString qstr;
  char *window_name;
  static int first = 1;

  // Initialize first time, save between invocations
  if (first){
    movie->dia = NULL;
    movie->reverse = 0;
    movie->longway = 0;
    movie->file_format = 0;
    movie->montage = 0;
    movie->frames = 10;
    movie->montFrames = 2;
    movie->overlap = 4;
    first = 0;
  }

  if (!state){
    if (movie->dia) 
      movie->dia->close();
    return;
  }
  if (movie->dia){
    movie->dia->raise();
    return;
  }

  // Initialize these every time
  movie->a = a;
  movie->saved   = 0;
  movie->abort = 1;   /* DNM: make this a flag that not making movie */

  movie->dia = new imodvMovieForm(imodvDialogManager.parent(IMODV_DIALOG), 
                                  Qt::Window);
  if (!movie->dia){
    dia_err("Failed to create 3dmodv movie window!");
    return;
  }

  // Set title bar
  window_name = imodwEithername("3dmodv Movie: ", a->imod->fileName, 1);
  qstr = window_name;
  if (window_name)
    free(window_name);
  if (!qstr.isEmpty())
    movie->dia->setWindowTitle(qstr);

  // Set the states
  imodvMovieSetStart();
  imodvMovieSetEnd();
  movie->dia->setButtonStates(movie->longway, movie->reverse, movie->montage,
                              movie->file_format, movie->saved);
  movie->dia->setFrameBoxes(movie->frames, movie->montFrames);
  imodvDialogManager.add((QWidget *)movie->dia, IMODV_DIALOG);
  adjustGeometryAndShow((QWidget *)movie->dia, IMODV_DIALOG);
}

static void setstep(int index, int frame, int loLim, int hiLim, float *start,
                    float *step)
{
  float tmin, tmax;
  movie->dia->readStartEnd(index, tmin, tmax);

  // If the item has a limit, make sure it is between 1 and limit
  if (hiLim && tmin < loLim) {
    tmin = (float)loLim;
    movie->dia->setStart(index, tmin);
  }
  if (hiLim && tmin > hiLim) {
    tmin = (float)hiLim;
    movie->dia->setStart(index, tmin);
  } 
  if (hiLim && tmax < loLim) {
    tmax = (float)loLim;
    movie->dia->setEnd(index, tmax);
  }
  if (hiLim && tmax > hiLim) {
    tmax = (float)hiLim;
    movie->dia->setEnd(index, tmax);
  }

  if (movie->reverse){
    *start = tmax;
    *step  = (tmin - tmax) / (float)frame;
  }else{
    *start = tmin;
    *step = (tmax - tmin) / (float)frame;
  }
}

void imodvMovieUpdate()
{
  if (movie->dia)
    movie->dia->setNonTifLabel();
}

static void imodvMakeMovie(int frames)
{
  ImodvApp *a = movie->a;
  Iview *vw;
  
  int frame;
  float astart, astep;
  float bstart, bstep;
  float gstart, gstep;
  float zstart, zstep, zfac;
  float xtstart, ytstart, ztstart;
  float xtstep, ytstep, ztstep;
  float xImStart, yImStart, zImStart;
  float xImStep, yImStep, zImStep;
  float thickStart, thickStep, transpStart, transpStep;
  double angle, delangle;
  double alpha, beta, gamma;
  Ipoint v;
  Imat *mat, *mati, *matp;

  if (frames <= 0)
    return;
  frame = frames - 1;
  if (!frame)
    frame = 1;

  xImStep = yImStep = zImStep = 0.;
  setstep(0, frame, 0, 0, &astart, &astep);
  setstep(1, frame, 0, 0, &bstart, &bstep);
  setstep(2, frame, 0, 0, &gstart, &gstep);
  setstep(3, frame, 0, 0, &xtstart, &xtstep);
  setstep(4, frame, 0, 0, &ytstart, &ytstep);
  setstep(5, frame, 0, 0, &ztstart, &ztstep);
  setstep(6, frame, 0, 0, &zstart, &zstep);
  if (!a->standalone) {
    setstep(7, frame, 1, a->vi->xsize, &xImStart, &xImStep);
    setstep(8, frame, 1, a->vi->ysize, &yImStart, &yImStep);
    setstep(9, frame, 1, a->vi->zsize, &zImStart, &zImStep);
    setstep(10, frame, 0, 100, &transpStart, &transpStep);
    setstep(11, frame, 1, a->vi->zsize, &thickStart, &thickStep);
  }

  a->md->xrotm = a->md->yrotm = a->md->zrotm = 0;
  a->movie = 0;
  a->moveall = 0;

  zfac = pow ((double)(zstart + zstep * frame) / zstart,
              1.0 / (double)frame);

  vw = a->imod->view;
  vw->rad   = zstart;
  vw->rot.x = astart;
  vw->rot.y = bstart;
  vw->rot.z = gstart;
  vw->trans.x = xtstart;
  vw->trans.y = ytstart;
  vw->trans.z = ztstart;
  mat = imodMatNew(3);
  mati = imodMatNew(3);
  matp = imodMatNew(3);

  if (!a->standalone) {
    a->vi->xmouse = (int)(xImStart - 0.5);
    a->vi->ymouse = (int)(yImStart - 0.5);
    a->vi->zmouse = (int)(zImStart - 0.5);
    imodvImageSetThickTrans((int)(thickStart + 0.5), (int)(transpStart + 0.5));
  }

  /* get incremental rotation matrix */

  delangle = 360. / frames;
  if (movie->reverse)
    delangle *= -1.0;

  if(movie->fullaxis == IMODV_MOVIE_FULLAXIS_X)
    imodMatRot(mati, delangle, b3dX);
  else if(movie->fullaxis == IMODV_MOVIE_FULLAXIS_Y)
    imodMatRot(mati, delangle, b3dY);
  else {

    /* In general case, net change is final matrix times inverse of starting
       matrix - then find the vector and angle for that rotation and
       divide angle by # of frames to get incremental matrix */

    imodMatRot(mat, (double)-astart, b3dX);
    imodMatRot(mat, (double)-bstart, b3dY);
    imodMatRot(mat, (double)-gstart, b3dZ);
    imodMatRot(mat, (double)(gstart + frame * gstep), b3dZ);
    imodMatRot(mat, (double)(bstart + frame * bstep), b3dY);
    imodMatRot(mat, (double)(astart + frame * astep), b3dX);
    imodMatFindVector(mat, &angle, &v);
    delangle = angle / frame;
    if (movie->longway)
      delangle = (angle - 360.) / frame;
    imodMatRotateVector(mati, delangle, &v);
  }

  /* Return if nothing is going to change */
  if (fabs((double)delangle) < 1.e-3 && !zstep && !xtstep && !ytstep &&
      !ztstep && !xImStep && !yImStep && !zImStep && !thickStep && !transpStep)
    return;


  movie->abort = 0;
  for(frame = 1; frame <= frames; frame++){
    if (movie->saved) {
      if (movie->file_format == 2)
        ImodPrefs->set2ndSnapFormat();
      imodv_auto_snapshot(QString::null, movie->file_format ? SnapShot_RGB : 
                          SnapShot_TIF);
      if (movie->file_format == 2)
        ImodPrefs->restoreSnapFormat();
    } else
      imodvDraw(a);

    xinput(); 

    if (movie->abort)
      break;

    /* DNM: don't change the angle after the last step */
    if (frame < frames){

      /* change zoom by a factor, not an increment */
      vw->rad   *= zfac;

      /* Get current rotation matrix, multiply by increment rotation,
         and convert back to angles */
      imodMatId(mat);
      imodMatRot(mat, (double)vw->rot.z, b3dZ);
      imodMatRot(mat, (double)vw->rot.y, b3dY);
      imodMatRot(mat, (double)vw->rot.x, b3dX);
      imodMatMult(mat, mati, matp);
      imodMatGetNatAngles(matp, &alpha, &beta, &gamma);

      vw->rot.x = alpha;
      vw->rot.y = beta;
      vw->rot.z = gamma;
      vw->trans.x += xtstep;
      vw->trans.y += ytstep;
      vw->trans.z += ztstep;
      if (!a->standalone) {
        a->vi->xmouse = (int)(xImStart + frame * xImStep - 0.5);
        a->vi->ymouse = (int)(yImStart + frame * yImStep - 0.5);
        a->vi->zmouse = (int)(zImStart + frame * zImStep - 0.5);
        imodvImageSetThickTrans((int)(thickStart + frame * thickStep + 0.5),
                                (int)(transpStart + frame * transpStep + 0.5));
      }
    }
  }
  movie->abort = 1;

  imodMatDelete(mat);
  imodMatDelete(mati);
  imodMatDelete(matp);
  return;
}

typedef struct {
  Ipoint transave;
  float radsave;
  Ipoint xunit;
  Ipoint yunit;
} MontModelData;

/* Routine to make a montage */
static void imodvMakeMontage(int frames, int overlap)
{
  ImodvApp *a = movie->a;
  Iview *vw;
  MontModelData *mmd;
  Imat *mat;
  Ipoint ipt, spt;
  float scrnscale;
  int ix, iy, xFullSize, yFullSize, numChunks, m, mstart, mend;
  float zoom, yzoom;
  unsigned char *framePix = NULL;
  unsigned char **fullPix = NULL;
  unsigned char **linePtrs = NULL;
  ScaleBar *barReal = scaleBarGetParams();
  ScaleBar barSaved;

  /* limit the overlap */
  if (frames <= 1)
    return;
  if (overlap < 0)
    overlap = 0;
  if (overlap > a->winx / 2)
    overlap = a->winx / 2;
  if (overlap > a->winy / 2)
    overlap = a->winy / 2;

  xFullSize = frames * a->winx - (frames - 1) * overlap;
  yFullSize = frames * a->winy - (frames - 1) * overlap;
  imodvModelDrawRange(a, mstart, mend);

  // Check for perspective and give error message
  for (m = mstart; m <= mend; m++) {
    vw = a->mod[m]->view;
    if (vw->fovy >= 1.0f) {
      imodError(NULL, "%s model has a perspective setting of %d (see "
                "Edit-Controls window).\nThe montage will not work right "
                "with perspective.", a->nm > 1 ? "One" : "This", (int)vw->fovy);
      return;
    }
  }

  a->md->xrotm = a->md->yrotm = a->md->zrotm = 0;
  a->movie = 0;
  a->moveall = 0;
  movie->abort = 0;
  mmd = B3DMALLOC(MontModelData, a->nm);
  if (!mmd) {
    imodError(NULL, "Failed to get memory for saving data per model.\n");
    return;
  }

  /* new zoom is minimum of zoom needed to get each dimension to work */
  zoom = (a->winx + (frames - 1) * (a->winx - overlap)) / (float)a->winx;
  yzoom = (a->winy + (frames - 1) * (a->winy - overlap)) / (float)a->winy;
  if (zoom > yzoom)
    zoom = yzoom;

  // Set up memory allocations and scale bar stuff now that zoom is known
  if (utilStartMontSnap(a->winx, a->winy, xFullSize, yFullSize, zoom, barSaved,
                        numChunks, &framePix, &fullPix, &linePtrs)) {
    imodError(NULL, "Failed to get memory for snapshot buffers.\n");
    free(mmd);
    return;
  }

  for (m = mstart; m <= mend; m++) {
    vw = a->mod[m]->view;

    /* Save current zoom and translations */
    mmd[m].radsave = vw->rad;
    mmd[m].transave = vw->trans;

    vw->rad /= zoom;

    /* Compute translation offsets implied by the given pixel shifts in X and
       Y in the display, using same code as imodv_translated */
    mat = imodMatNew(3);
    imodMatId(mat);
    imodMatRot(mat, -(double)vw->rot.x, b3dX);
    imodMatRot(mat, -(double)vw->rot.y, b3dY);
    imodMatRot(mat, -(double)vw->rot.z, b3dZ);
    
    scrnscale = 0.5 * B3DMIN(a->winx, a->winy) / vw->rad;
    
    spt.x = 1.0f/scrnscale;
    spt.y = 1.0f/scrnscale;
    spt.z = 1.0f/scrnscale * 1.0f/a->mod[m]->zscale;
    imodMatScale(mat, &spt);
    
    ipt.x = a->winx - overlap;
    ipt.y = 0.;
    ipt.z = 0.;
    imodMatTransform(mat, &ipt, &mmd[m].xunit);
    mmd[m].xunit.x *= (1.0/ vw->scale.x);
    mmd[m].xunit.y *= (1.0/ vw->scale.y);
    mmd[m].xunit.z *= (1.0/ vw->scale.z);

    ipt.x = 0.;
    ipt.y = a->winy - overlap;
    imodMatTransform(mat, &ipt, &mmd[m].yunit);
    mmd[m].yunit.x *= (1.0/ vw->scale.x);
    mmd[m].yunit.y *= (1.0/ vw->scale.y);
    mmd[m].yunit.z *= (1.0/ vw->scale.z);
    
    /* do initial displacement to lower left corner */
    vw->trans.x += 0.5 * (frames - 1.) * (mmd[m].xunit.x + mmd[m].yunit.x) ;
    vw->trans.y += 0.5 * (frames - 1.) * (mmd[m].xunit.y + mmd[m].yunit.y) ;
    vw->trans.z += 0.5 * (frames - 1.) * (mmd[m].xunit.z + mmd[m].yunit.z) ;
  }

  /* 12/28/03: Disabling the auto swap and reading from back buffer for db 
     did not work here for protecting from occluding stuff (nor does it work 
     in regular snapshot) */
  // But 4/6/05: It was needed to prevent getting an out-of-date image for
  // one machine under xorg-6.7.0
  if (a->db)
    a->mainWin->mCurGLw->setBufferSwapAuto(false);
  glReadBuffer(a->db ? GL_BACK : GL_FRONT);

  for (iy = 0; iy < frames; iy++) {
    for (ix = 0; ix < frames; ix++) {

      // Set up for scale bar if it is the right corner
      utilMontSnapScaleBar(ix, iy, frames, a->winx, a->winy, scrnscale,
                           barSaved.draw);
      imodvDraw(a);

      // Print scale bar length if it was drawn
      if (a->scaleBarSize > 0)
        imodPrintStderr("Scale bar for montage is %g %s\n", a->scaleBarSize,
                        imodUnits(a->imod));
      glReadPixels(0, 0, a->winx, a->winy, GL_RGBA, GL_UNSIGNED_BYTE, 
                   framePix);
      glFlush();
      memLineCpy(linePtrs, framePix, a->winx, a->winy, 4, ix * 
                 (a->winx - overlap), iy * (a->winy - overlap), a->winx, 0, 0);
        
      xinput(); 
      if (ImodvClosed) {
        free(mmd);
        // Shouldn't it do this too?
        utilFreeMontSnapArrays(fullPix, numChunks, framePix, linePtrs);
        return;
      }

      if (movie->abort)
        break;

      /* Each X, advance along row */
      for (m = mstart; m <= mend; m++) {
        vw = a->mod[m]->view;
        vw->trans.x -= mmd[m].xunit.x;
        vw->trans.y -= mmd[m].xunit.y;
        vw->trans.z -= mmd[m].xunit.z;
      }
    }

    /* End of row: advance in Y, move X back to start of next row */
    for (m = mstart; m <= mend; m++) {
      vw = a->mod[m]->view;
      vw->trans.x -= mmd[m].yunit.x - frames * mmd[m].xunit.x;
      vw->trans.y -= mmd[m].yunit.y - frames * mmd[m].xunit.y;
      vw->trans.z -= mmd[m].yunit.z - frames * mmd[m].xunit.z;
    }
    if (movie->abort)
      break;
  }

  /* If not aborted, then get snapshot name and save data */
  if (!movie->abort)
    utilFinishMontSnap(linePtrs, xFullSize, yFullSize, 
                       movie->file_format, a->snap_fileno, 4, zoom,
                       "modv", "3dmodv: Saving");
  
  utilFreeMontSnapArrays(fullPix, numChunks, framePix, linePtrs);
  
  if (a->db) {
    imodv_swapbuffers(a);
    a->mainWin->mCurGLw->setBufferSwapAuto(true);
  }
  movie->abort = 1;
  for (m = mstart; m <= mend; m++) {
    vw = a->mod[m]->view;
    vw->rad = mmd[m].radsave;
    vw->trans = mmd[m].transave;
  }
  *barReal = barSaved;
  imodvDraw(a);
  imodMatDelete(mat);
  free(mmd);
  return;
}