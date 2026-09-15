package com.zksaga.foldlight;
import org.junit.Test;
import static org.junit.Assert.*;

public class FoldDeviceProfileTest {
    @Test public void onlyMeasuredExactModelsEnablePrivilegedProfiles(){
        assertNotNull(FoldDeviceProfile.forModel("SM-F9710"));
        assertNotNull(FoldDeviceProfile.forModel("SM-F9660"));
        for(String model:new String[]{null,"","SM-F966B","SM-F966U","SM-F9710-test","sm-f9660","Pixel Fold"})
            assertNull(FoldDeviceProfile.forModel(model));
    }
    @Test public void fold8PanelDimensionsAndConcurrentStateStayUnchanged(){
        FoldDeviceProfile p=FoldDeviceProfile.forModel("SM-F9710");
        assertTrue(p.isCover(1248,1972));assertTrue(p.isCover(1972,1248));
        assertTrue(p.isInner(2448,1848));assertEquals(1,p.secondaryDisplayId);assertEquals(5,p.concurrentOuterState);
        assertFalse(p.isCover(1080,2520));assertFalse(p.isInner(1968,2184));
        assertEquals(1248,p.width(true));assertEquals(1848,p.height(false));
    }
    @Test public void fold7PortraitInnerPanelCannotBeMistakenForCover(){
        FoldDeviceProfile p=FoldDeviceProfile.forModel("SM-F9660");
        assertTrue(p.isCover(1080,2520));assertTrue(p.isCover(2520,1080));
        assertTrue(p.isInner(1968,2184));assertTrue(p.isInner(2184,1968));
        assertFalse(p.isCover(1968,2184));assertFalse(p.isInner(1080,2520));
        assertEquals(1080,p.width(true));assertEquals(2520,p.height(true));
        assertEquals(1968,p.width(false));assertEquals(2184,p.height(false));
        assertTrue(FoldMath.isCoverSurface(1080,2520,"SM-F9660"));
        assertFalse(FoldMath.isCoverSurface(1968,2184,"SM-F9660"));
        assertFalse(FoldMath.isCoverSurface(1080,2520,"SM-F966B"));
    }
    @Test public void matchingResolutionOnAnotherDisplayDoesNotAuthorizeIt(){
        FoldDeviceProfile p=FoldDeviceProfile.forModel("SM-F9660");
        assertTrue(p.isSecondaryPanel(1,1968,2184));
        assertTrue(p.isSecondaryPanel(1,1080,2520));
        assertFalse(p.isSecondaryPanel(0,1968,2184));
        assertFalse(p.isSecondaryPanel(2,1968,2184));
        assertFalse(p.isSecondaryPanel(1,2448,1848));
        assertFalse(p.isSecondaryPanel(1,1920,1080));
    }
}
