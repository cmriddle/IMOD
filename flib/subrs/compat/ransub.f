      REAL FUNCTION RAN(ISEED)
      INTEGER ISEED,IBEENHERE,FIRST
      REAL VAL
      DATA IBEENHERE,FIRST /0,0/
      SAVE IBEENHERE,FIRST
      IF (FIRST.EQ.0.OR.IBEENHERE.NE.ISEED) THEN
        CALL MYSRAND(ISEED,VAL)
        IBEENHERE = ISEED
      ELSE
        CALL MYRAND(ISEED,VAL)
      END IF
      IF (FIRST.EQ.0) FIRST = 1
      RAN = VAL
C       write(6,100) IBEENHERE,FIRST,RAN
C       100     FORMAT(1X,"RAN: IBEENHERE: ",I10,4X,"FIRST: ",I10,3X,"val: ",
C       &  F18.8)
      END

