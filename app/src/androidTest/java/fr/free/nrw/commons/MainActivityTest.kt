package fr.free.nrw.commons

import androidx.test.rule.ActivityTestRule
import androidx.test.runner.AndroidJUnit4
import fr.free.nrw.commons.contributions.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.content.pm.ActivityInfo


@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    public var activityRule = ActivityTestRule(MainActivity::class.java)

    @Test
    fun orientationChange(){
        UITestHelper.getOrientation(activityRule)
    }
}