#include "dialog_frame.h"
class QPushButton;
class QCheckBox;

class QLabel;
class QSpinner;
class QVButtonGroup;
class QButtonGroup;
class QRadioButton;
class QGridLayout;
class QVBoxLayout;
class QComboBox;
class QSpinBox;
class QGroupBox;
class QVBoxLayout;
class QSpacerItem;
class QEvent;

#include "imodplugin.h"
#include "dia_qtutils.h"
#include "icontextra.h"

#include <qstring.h>
#include <vector>
using namespace std;

//############################################################


//-------------------------------
//## INTERPOLATOR WINDOW:

class DrawingTools : public DialogFrame
{
  Q_OBJECT

 public:
  DrawingTools(QWidget *parent, const char *name = NULL);
  ~DrawingTools() {};
  
public slots:
  void buttonPressed(int);
  void loadSettings();
  void saveSettings();

  bool drawExtraObject( bool redraw );
  void reduceCurrentContour();
  void smoothCurrentContour();
  void reduceConts();
  void smoothConts();
  void selectNextOverlappingContour();
  void printModelPointInfo();
  void moreActions();
  void moreSettings();
  void test();
  void cut();
  void copy();
  void paste(bool centerOnMouse);
  
  void changeType( int value );
  void changeTypeSelected( int newType );
  void changeMaxArea( int value );
  void changeSmoothPtsDist( int value );
  void changeSmoothTensileFract( int value );
  void changeReducePts();
  
  void changeDeformCircleRadius( float value );
  void clearExtraObj();
  
 protected:
  void closeEvent ( QCloseEvent * e );
  void keyPressEvent ( QKeyEvent * e );
  void keyReleaseEvent ( QKeyEvent * e );
  
 private:
  
  QVButtonGroup *typeButtonGroup;
  QRadioButton *typeRadio_Normal;
  QRadioButton *typeRadio_Deform;
  QRadioButton *typeRadio_Join;
  QRadioButton *typeRadio_Transform;
  QRadioButton *typeRadio_Eraser;
  
  QGroupBox   *grpOptions;
  QGridLayout *gridLayout1;
  QLabel      *lblMaxArea;
  QSpinBox    *maxAreaSpinner;
  QCheckBox   *reducePtsCheckbox;
  QLabel      *lblSmoothPtsDist;
  QSpinBox    *smoothPtsDist;
  QLabel      *lblSmoothTensileFract;
  QSpinBox    *smoothTensileFract;
  
  QGroupBox   *grpActions;
  QVBoxLayout *vboxLayout1;
  QPushButton *reduceContsButton;
  QPushButton *smoothContsButton;
  
  QWidget     *widget1;
  QGridLayout *gridLayout2;
  QPushButton *moreActionsButton;
  QPushButton *moreSettingsButton;
};


//-------------------------------
//## CONSTANTS:

enum drawmodes      { DM_NORMAL, DM_DEFORM, DM_JOIN, DM_TRANSFORM, DM_ERASER,
                      DM_RESIZEPT };
enum wheelbehaviour { WH_NONE, WH_DEFORMCIRCLE, WH_SLICES, WH_CONTS, WH_PTSIZE };

const int NUM_SAVED_VALS = 9;

//-------------------------------
//## DRAWINGTOOLS DATA STRUCTURE:

struct DrawingToolsData   // contains all local plugin data
{
  ImodView    *view;
  DrawingTools *window;
  
  //## DRAWING OPTIONS:
  
  int drawMode;        // the type of drawing tool currently selected (see enum "drawmodes")
  
  int    draw_reducePts;            // if 1: drawn conts will automatically be reduced
  float  draw_reducePtsMaxArea;     // max area which must be formed by three consecutive
                                    //  pts in a contour else the middle one be removed
                                    //  in the "cont_reducePtsMinArea" function
  
  float  draw_smoothMinDist;        // min distance between consecutive points
  float  draw_smoothTensileFract;   // tensile fraction used by the catumull-rom spline
                                    //  algorithm in the "cont_addPtsSmooth" function
                                    //  NOTE: 0=straight, 1.5=smooth, 2.0>=very bendy
  float  draw_deformRadius;         //  the radius, in pixels, of the deformation circle
  
  
  //## SETTINGS:
  
  int  wheelBehav;              // changes the behaviour of the mouse wheel
                                //   (see: wheelbehaviour)
  
  bool   useNumKeys;            // intercepts number keys [1]-[5] to change draw mode
  int    wheelResistance;       // the higher the value, the slower mouse scrolling works
  bool   showMouseInModelView;  // shows the extra object in the model view
  int    selectedAction;        // the last selected action under "More Actions"
  
  //## MOUSE:
  
  Ipoint mouse;         // the current tomogram coordinates of the mouse
  Ipoint mousePrev;     // the mosue coordinates before it's last move
  Ipoint mouseDownPt;   // the mouse coordinates when the mouse button was last pressed
  float changeX;        // the distance (in tomogram pixels) of the last mouse move in X
  float changeY;        // the distance (in tomogram pixels) of the last mouse move in Y
  
  int but1Down;    // used for selecting points & panning (in NORMAL mode)
  int but2Down;    // used for drawing new contours       (in NORMAL mode)
  int but3Down;    // used for modifying points           (in NORMAL mode)
  
  bool but1Pressed;     //|- if true: button was just pressed
  bool but2Pressed;     //|
  bool but3Pressed;     //|
  
  bool but1Released;    //|- if true: button was just released
  bool but2Released;    //|
  bool but3Released;    //|
  
  //## OTHER:
  
  bool shiftDown;       // set to true when the SHIFT button is down
  Ipoint centerPt;      // the center of the currently selected contour
  
  Icont *copiedCont;      // used to cut/copy and paste contours
  Ipoint copiedCenterPt;  // the center of area of the last contour cut/copied
  
  bool initialized;           // is set to true after values have been set
  int xsize, ysize, zsize;    // size of the image / tomogram
  int extraObjNum;            // stores a reference to the extra object
};




//############################################################




//-------------------------------
//## GUI FUNCTIONS:

string qStringToString( QString qstr );
void MsgBox( string str );
bool MsgBoxYesNo( QWidget *parent, string str );
string InputBoxString( QWidget *parent, string title, string label, string defaultStr );


//-------------------------------
//## SMALL FUNCTIONS:

Iobj *getCurrObj();
Icont *getCurrCont();
bool isCurrObjValidAndShown();
bool isCurrContValid();


//-------------------------------
//## EDITING FUNCTIONS:

int edit_getZOfTopZap();
int edit_setZapLocation( float x, int y, int z, bool redraw );
int edit_changeSelectedSlice( int changeZ, bool redraw );

int edit_addContourToObj( Iobj *obj, Icont *cont, bool enableUndo );
int edit_removeAllFlaggedContoursFromObj( Iobj *obj );
bool edit_selectContourPtNearCoords(float x, float y, int z, float distTolerance);

bool edit_copiedContIfDiffSlice( bool selectNewCont );

void edit_executeDeformStart();
void edit_executeDeform();
void edit_executeDeformPush( Ipoint center, float radius );
void edit_executeDeformPinch( Ipoint center, float radius );
void edit_executeDeformEnd();
void edit_executeJoinEnd();

void edit_inversePointsInContour(bool reorder);
bool edit_reduceCurrContour();
bool edit_smoothCurrContour();

bool edit_doesBBoxTouchCircle( Ipoint *ll, Ipoint *ur, Ipoint *center, float radius );
int edit_eraseContsInCircle( Ipoint center, float radius);
int edit_erasePointsInCircle( Ipoint center, float radius );
bool edit_breakPointsInCircle( Ipoint center, float radius );

void edit_breakCurrContIntoSimpleContsAndDeleteSmallest ();    
void edit_makeCurrContSimple ();                
void edit_deleteCurrContIfTooSmall();              
void edit_joinCurrContWithAnyTouching();            

bool edit_selectNextOverlappingCont();




//############################################################
