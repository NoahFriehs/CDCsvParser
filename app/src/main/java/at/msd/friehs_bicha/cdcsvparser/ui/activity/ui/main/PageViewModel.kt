package at.msd.friehs_bicha.cdcsvparser.ui.activity.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import at.msd.friehs_bicha.cdcsvparser.instance.InstanceVars

/**
 * ViewModel for the OverviewActivity
 */
class PageViewModel : ViewModel() {

    private val _index = MutableLiveData<Int>()
    val text: LiveData<String> = Transformations.map(_index) {
        val it = it -1  // index starts at 1 go fuck my life
        if (it >= TAB_TITLES.size || it < 0 || InstanceVars.applicationContext == null) {
            return@map ""
        }

        "Hello world from section: ${InstanceVars.applicationContext.resources.getString(TAB_TITLES[it])}"
    }

    fun setIndex(index: Int) {
        _index.value = index
    }
}