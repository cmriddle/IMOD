* * * * * ROTATEVOL * * * * *
c	  
c	  ROTATEVOL will rotate all or part of a three-dimension volume
c	  of data.  The rotations may be by any angles about the three axes.
c	  Tilt angles and origin information in the header are properly
c	  maintained so that the new data stack will have a coordinate system
c	  congruent with the old one.
c	  
c	  Before running the program, examine the data set to determine
c	  what rotations you desire.  The overall rotation of the data set is
c	  specified by tilt angles for three successive rotations about the
c	  the 3 axes, with rotation first around Z, then around Y, then around
c	  X.  Positive angles give counterclockwise rotations (looking down
c	  an axis toward the origin).  If you only want part of the data set,
c	  determine the center of the region that you want to extract.  This
c	  region is specified by the index coordinates of that point in the
c	  input file (position the cursor on the desired point in WIMP, press
c	  a mouse button, and record the CX and CY values on the screen, and
c	  the section number.)
c
c	  INPUTS to the program:
c	  
c	  Name of the input file with data to be rotated
c
c	  Name of the output file for rotated data
c
c	  Path name of directory (for example, /usr/tmp) where temporary files
c	  .  can be placed, or Return to have files placed in the current
c	  .  directory
c
c	  X, Y, and Z dimensions of the output file
c	  
c	  Index coordinates of the center of the region to be rotated in the
c	  .  input file, or / to use the coordinates of the center of the file
c	  
c	  Rotations around the Z, Y, and X axes
c	  
c	  The program can work on an arbitrarily large volume.  It reconstructs
c	  a series of sub-cubes of the output volume (each cube is about
c	  160x160x160).  For each cube, it reads into memory a cube from the
c	  input volume that contains all of the image area that rotates into
c	  that cube of output volume.  It then uses triquadratic interpolation
c	  to find each pixel of the output cube, and writes the cube to a
c	  scratch file.  When all of the cubes in one layer are done, it reads
c	  back data from the scratch files and assembles each section in that
c	  layer.
c	  
c	  David Mastronarde 7/25/91
c	  1995: switch to tri-quadratic interpolation, allow real center coords
c	  DNM 2/26/01: add temporary directory entry and semi-unique filenames
c	  DNM 11/6/01: fixed problem with output array size not being respected
c
c	  $Author$
c
c	  $Date$
c
c	  $Revision$
c
c	  $Log$
c	  Revision 3.2  2003/03/15 01:04:15  mast
c	  Standardize error output
c	

c	  keep limdim less than 2/3 of inpdim**2
	parameter (inpdim=256,limdim=16200,lmcube=limdim/inpdim)
	parameter (limout=(inpdim*3)/2)
	real*4 array(inpdim,inpdim,inpdim),brray(limout,limout)
	real*4 cxyzin(3),cxyzout(3),cell(6),title(20)
	integer*4 nxyzin(3),mxyzin(3),nxyzout(3)
	real*4 indcen(3)
	common /xyz/nxin,nyin,nzin,nxout,nyout,nzout,cxin,cyin,czin
     &	    ,cxout,cyout,czout
	equivalence (nxyzin(1),nxin),(nxyzout(1),nxout)
	equivalence (cxyzin(1),cxin),(cxyzout(1),cxout)
	real*4 mfor(3,3),minv(3,3),mold(3,3),mnew(3,3),moldinv(3,3)
	real*4 angles(3),tiltold(3),tiltnew(3),orig(3),xtmp(3),delta(3)
	integer*4 ncubes(3),nxyzcubas(3),nxyzscr(3),nbigcube(3)
	integer*4 nxyzcube(3,lmcube),ixyzcube(3,lmcube),izsec(4)
	integer*4 inmin(3),inmax(3)
	integer*2 ifile(lmcube,lmcube),izinfile(lmcube,lmcube,limout)
c
	character*120 filein,fileout,tempdir,tempext,tempname
	character*120 temp_filename
	common /bigarr/array
c
c	DNM 3/8/01: initialize the time in case time(tim) doesn't work
c
	character dat*9,tim*8/'00:00:00'/
c
c 7/7/00 CER: remove the encode's; titlech is the temp space
c
        character*80 titlech
c
	write(*,'(1x,a,$)')'Name of input file: '
	read(5,'(a)')filein
	call imopen(5,filein,'RO')
	call irdhdr(5,nxyzin,mxyzin,mode,dminin,dmaxin,dmeanin)
c
	write(*,'(1x,a,$)')'Name of output file: '
	read(5,'(a)')fileout
	call imopen(6,fileout,'NEW')
c
	write(*,'(1x,a,/,a,$)')'Enter path name of directory for '//
     &	    'temporary files, ',' or Return to use current directory: '
	read(5,'(a)')tempdir
c
	write(*,'(1x,a,$)')'X, Y, and Z dimensions of the output file: '
	read(5,*)nxout,nyout,nzout
c	  
	do i=1,3
	  indcen(i)=nxyzin(i)/2
	enddo
	write(*,'(1x,a,/,a,$)')'Enter X, Y, and Z index coordinates of '
     &	    //'the center of rotation',
     &	    '   in the input file (/ for center of file): '
	read(5,*)(indcen(i),i=1,3)
c	  
c	  get true centers of index coordinate systems
c
	call irtdel(5,delta)
	do i=1,3
	  cxyzin(i)=(nxyzin(i)-1)/2.+indcen(i)-nxyzin(i)/2
	  cxyzout(i)=(nxyzout(i)-1)/2.
	  cell(i)=nxyzout(i)*delta(i)
	  cell(i+3)=90.
	enddo
c
	print *,'Rotations are applied in the order that you',
     &	    ' enter them: rotation about the','   Z axis, then ',
     &	    'rotation about the Y axis, then rotation about the X axis'
	write(*,'(1x,a,$)')
     &	    'Rotations about Z, Y, and X axes (gamma, beta, alpha): '
	read(5,*)angles(3),angles(2),angles(1)
c	  
c	  get matrices for forward and inverse rotations
c	  
	call icalc_matrix(angles,mfor)
	call inv_matrix(mfor,minv)
c	  
	call icrhdr(6,nxyzout,nxyzout,mode,title,0)
	call ialcel(6,cell)
	call itrlab(6,5)
	call time(tim)
	call date(dat)
c
c 7/7/00 CER: remove the encodes
c
c       ENCODE(80,302,TITLE)(angles(i),i=1,3),dat,tim
        write(titlech,302) (angles(i),i=1,3),dat,tim
        read(titlech,'(20a4)')(TITLE(kti),kti=1,20)
302	FORMAT('ROTATEVOL: 3D rotation by angles:',3f7.1,t57,a9,2x,a8)
	dmin=1.e20
	dmax=-dmin
	tsum=0.
c	  
c	  calculate new tilt angles and origin information from old
c	  
	call irttlt(5,tiltold)
	call icalc_matrix(tiltold,mold)
	call inv_matrix(mold,moldinv)
	call ialtlt_orig(6,tiltold)
	call ialtlt(6,tiltold)
	call ialtlt_rot(6,angles)
	call irttlt(6,tiltnew)
	call icalc_matrix(tiltnew,mnew)
	call irtorg(5,orig(1),orig(2),orig(3))
	xcen=cxin-orig(1)
	ycen=cyin-orig(2)
	zcen=czin-orig(3)
	do i=1,3
	  xtmp(i)=moldinv(i,1)*xcen+moldinv(i,2)*ycen+moldinv(i,3)*zcen
	enddo
	do i=1,3
	  orig(i)=cxyzout(i)-
     &	      (mnew(i,1)*xtmp(1)+mnew(i,2)*xtmp(2)+mnew(i,3)*xtmp(3))
	enddo
	call ialorg(6,orig(1),orig(2),orig(3))
c	  
c	  find maximum extent in input volume occupied by a back-transformed
c	  unit cube in output volume
c
	devmx=0.
	do ix=-1,1,2
	  do iy=-1,1,2
	    do i=1,3
	      devmx=max(devmx,abs(minv(i,1)*ix+minv(i,2)*iy+minv(i,3)))
	    enddo
	  enddo
	enddo
	idimout=inpdim/devmx-2
	idimout=min(idimout,limout)
c	  
c	  now compute sizes of nearly equal sized near cubes to fill output
c	  volume, store the starting index coordinates
c
	do i=1,3
	  ncubes(i)=(nxyzout(i)-1)/idimout+1
	  if(ncubes(i).gt.lmcube) call errorexit(
     &	      'TOO MANY CUBES IN LONGEST DIRECTION TO FIT IN ARRAYS')

	  nxyzcubas(i)=nxyzout(i)/ncubes(i)
	  nbigcube(i)=mod(nxyzout(i),ncubes(i))
	  ind=0
	  do j=1,ncubes(i)
	    ixyzcube(i,j)=ind
	    nxyzcube(i,j)=nxyzcubas(i)
	    if(j.le.nbigcube(i))nxyzcube(i,j)=nxyzcube(i,j)+1
	    ind=ind+nxyzcube(i,j)
	  enddo
	  nxyzscr(i)=nxyzcubas(i)+1
	enddo
	if (nxout * (nxyzcubas(2) + 1) .ge. inpdim**3)
     &	    call errorexit('OUTPUT IMAGE TOO WIDE FOR ARRAYS')

	write(*,103)ncubes(3),ncubes(1),ncubes(2)
103	format(' Rotations done in',i3,' layers, with',i3,' by',i3,
     &	    ' cubes in each layer')
c	  
c	  open scratch files with 4 different sizes.
c	  compose temporary filenames from the time
c	  
	tempext='rot      1'
	tempext(4:5)=tim(1:2)
	tempext(6:7)=tim(4:5)
	tempext(8:9)=tim(7:8)
	tempname=temp_filename(filein,tempdir,tempext)
c
	nxyzscr(3)=nxyzscr(3)*ncubes(1)*ncubes(2)
	call ialprt(.false.)
	call imopen(1,tempname,'scratch')
	call icrhdr(1,nxyzscr,nxyzscr,mode,title,0)
c	  
	tempext(10:10)='2'
	tempname=temp_filename(filein,tempdir,tempext)
	nxyzscr(1)=nxyzscr(1)-1
	call imopen(2,tempname,'scratch')
	call icrhdr(2,nxyzscr,nxyzscr,mode,title,0)
c	  
	tempext(10:10)='4'
	tempname=temp_filename(filein,tempdir,tempext)
	nxyzscr(2)=nxyzscr(2)-1
	call imopen(4,tempname,'scratch')
	call icrhdr(4,nxyzscr,nxyzscr,mode,title,0)
c	  
	tempext(10:10)='3'
	tempname=temp_filename(filein,tempdir,tempext)
	nxyzscr(1)=nxyzscr(1)+1
	call imopen(3,tempname,'scratch')
	call icrhdr(3,nxyzscr,nxyzscr,mode,title,0)
c	  
c	  get an array of file numbers for each cube in X/Y plane
c
	do ix=1,ncubes(1)
	  do iy=1,ncubes(2)
	    ifile(ix,iy)=1
	    if(ix.gt.nbigcube(1))ifile(ix,iy)=ifile(ix,iy)+1
	    if(iy.gt.nbigcube(2))ifile(ix,iy)=ifile(ix,iy)+2
	  enddo
	enddo
c	  
c	  loop on layers of cubes in Z, do all I/O to complete layer
c	  
	do izcube=1,ncubes(3)
c	    
c	    initialize files and counters
c	    
	  do i=1,4
	    call imposn(i,0,0)
	    izsec(i)=0
	  enddo
c	    
c	    loop on the cubes in the layer
c
	  do ixcube=1,ncubes(1)
	    do iycube=1,ncubes(2)
c		
c		back-transform the corner coordinates of the output cube to
c		find the limiting index coordinates of the input cube
c		
	      do i=1,3
		inmin(i)=100000
		inmax(i)=-100000
	      enddo
	      do ifx=0,1
		do ify=0,1
		  do ifz=0,1
		    xcen=ixyzcube(1,ixcube)+ifx*nxyzcube(1,ixcube)-cxout
		    ycen=ixyzcube(2,iycube)+ify*nxyzcube(2,iycube)-cyout
		    zcen=ixyzcube(3,izcube)+ifz*nxyzcube(3,izcube)-czout
		    do i=1,3
		      ival=nint(minv(i,1)*xcen+minv(i,2)*ycen+
     &			  minv(i,3)*zcen+cxyzin(i))
		      inmin(i)=min(max(0,min(inmin(i),ival-1)),
     &			  nxyzin(i)-1)
		      inmax(i)=max(min(nxyzin(i)-1,max(inmax(i),ival+1))
     &			  ,0)
		    enddo
		  enddo
		enddo
	      enddo
	      ifempty=0
	      do i=1,3
		if(inmin(i).gt.inmax(i))ifempty=1
	      enddo
c		
c		load the input cube
c		
	      if(ifempty.eq.0)then
		do iz=inmin(3),inmax(3)
		  call imposn(5,iz,0)
		  call irdpas(5,array(1,1,iz+1-inmin(3)),inpdim,inpdim,
     &		      inmin(1),inmax(1),inmin(2),inmax(2),*99)
		enddo
	      endif
c		
c		prepare offsets and limits
c
	      xofsout=ixyzcube(1,ixcube)-1-cxout
	      xofsin=cxin+1-inmin(1)
	      yofsin=cyin+1-inmin(2)
	      zofsin=czin+1-inmin(3)
	      ixlim=inmax(1)+1-inmin(1)
	      iylim=inmax(2)+1-inmin(2)
	      izlim=inmax(3)+1-inmin(3)
c		
c		loop over the output cube, doing and saving one section at a
c		time
c		
c
	      do iz=1,nxyzcube(3,izcube)
		zcen=ixyzcube(3,izcube)+iz-1-czout
		do iy=1,nxyzcube(2,iycube)
		  ycen=ixyzcube(2,iycube)+iy-1-cyout
		  if(ifempty.eq.0)then
		    do ix=1,nxyzcube(1,ixcube)
		      xcen=ix+xofsout
c			
c			get indices in array of input data
c			
		      xp=minv(1,1)*xcen+minv(1,2)*ycen+minv(1,3)*zcen+
     &			  xofsin
		      yp=minv(2,1)*xcen+minv(2,2)*ycen+minv(2,3)*zcen+
     &			  yofsin
		      zp=minv(3,1)*xcen+minv(3,2)*ycen+minv(3,3)*zcen+
     &			  zofsin
		      bval = DMEANIN
c			
c			do triquadratic interpolation with higher-order terms 
c			omitted
c			
		      ixp=nint(xp)
		      iyp=nint(yp)
		      izp=nint(zp)
		      IF (IXP.GE.1 .AND. IXP.Le.IXLIM .AND.
     &			  IYP.GE.1 .AND. IYP.Le.IYLIM .AND.
     &			  IZP.GE.1 .AND. IZP.Le.IZLIM) THEN
			dx=xp-ixp
			dy=yp-iyp
			dz=zp-izp
			ixpp1=min(ixlim,ixp+1)
			iypp1=min(iylim,iyp+1)
			izpp1=min(izlim,izp+1)
			ixpm1=max(1,ixp-1)
			iypm1=max(1,iyp-1)
			izpm1=max(1,izp-1)
C			  
C			  Set up terms for quadratic interpolation
C			  
			V2 = ARRAY(IXP, IYPM1, IZP)
			V4 = ARRAY(IXPM1, IYP, IZP)
			V5 = ARRAY(IXP, IYP, IZP)
			V6 = ARRAY(IXPP1, IYP, IZP)
			V8 = ARRAY(IXP, IYPP1, IZP)
			VU = ARRAY(IXP, IYP, IZPP1)
			VD = ARRAY(IXP, IYP, IZPM1)
			vmax=max(v2,v4,v5,v6,v8,vu,vd)
			vmin=min(v2,v4,v5,v6,v8,vu,vd)
C			  
			C = (V6 - V4)*.5
			A = C + V4 - V5
			D = (V8 - V2)*.5
			B = D + V2 - V5
			F = (VU - VD)*.5
			E = F + VD - V5
			bval = (a*dx+c)*dx + (b*dy+d)*dy
     &			    + (e*dz+f)*dz + v5
			if(bval.gt.vmax)bval=vmax
			if(bval.lt.vmin)bval=vmin
		      endif
		      brray(ix,iy)=bval
		    enddo
		  else
		    do ix=1,nxyzcube(1,ixcube)
		      brray(ix,iy)=dmeanin
		    enddo
		  endif
		enddo
		iunit=ifile(ixcube,iycube)
		call irepak(brray,brray,limout,limout,
     &		    0,nxyzcube(1,ixcube)-1,0,nxyzcube(2,iycube)-1)
		call iwrsec(iunit,brray)
		izinfile(ixcube,iycube,iz)=izsec(iunit)
		izsec(iunit)=izsec(iunit)+1
	      enddo
	    enddo
	  enddo
c	    
c	    whole layer of cubes in z is done.  now reread and compose one
c	    row of the output section at a time in array
c	    
	  do iz=1,nxyzcube(3,izcube)
	    do iycube=1,ncubes(2)
	      nLinesOut = nxyzcube(2,iycube)
	      do ixcube=1,ncubes(1)
		iunit=ifile(ixcube,iycube)
		longint=izinfile(ixcube,iycube,iz)
		call imposn(iunit,longint,0)
		call irdsec(iunit,brray,*99)
		call pack_piece(array,nxout,nyout,ixyzcube(1,ixcube),
     &		    0,brray,nxyzcube(1,ixcube), nLinesOut)
	      enddo
	      call iclden(array,nxout,nLinesOut,1,nxout,1,nLinesOut,tmin,tmax,
     &		  tmean)
	      dmin=min(dmin,tmin)
	      dmax=max(dmax,tmax)
	      tsum=tsum+tmean
	      call iwrsecl(6,array, nLinesOut)
	    enddo
	  enddo
 	enddo
c
	dmean=tsum/nzout
	call iwrhdr(6,title,1,dmin,dmax,dmean)
	do i=1,6
	  call imclose(i)
	enddo
	call exit(0)
99	call errorexit('READING FILE')
	end

	subroutine pack_piece (array,ixdout,iydout,ixofs,iyofs,
     &	    brray,nxin,nyin)
	real*4 array(ixdout,iydout),brray(nxin,nyin)
	do iy=1,nyin
	  do ix=1,nxin
	    array(ix+ixofs,iy+iyofs)=brray(ix,iy)
	  enddo
	enddo
	return
	end

	subroutine errorexit(message)
	character*(*) message
	print *
	print *,'ERROR: ROTATEVOL - ',message
	call exit(1)
	end

