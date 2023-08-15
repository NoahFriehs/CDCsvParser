package at.msd.friehs_bicha.cdcsvparser.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.ViewPager
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.SettingsActivity
import at.msd.friehs_bicha.cdcsvparser.databinding.ActivityOverviewBinding
import at.msd.friehs_bicha.cdcsvparser.ui.activity.ui.main.SectionsPagerAdapter
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout

class OverviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOverviewBinding

    private var hasData = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityOverviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sectionsPagerAdapter = SectionsPagerAdapter(this, supportFragmentManager)
        val viewPager: ViewPager = binding.viewPager
        viewPager.adapter = sectionsPagerAdapter
        val tabs: TabLayout = binding.tabs
        tabs.setupWithViewPager(viewPager)
        val fabAdd: FloatingActionButton = binding.fabAdd

        fabAdd.setOnClickListener { view ->
            Snackbar.make(view, "Replace with add action TODO", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }

        hasData = PreferenceHelper.getIsAppModelSavedLocal(applicationContext)

        if (hasData)
        {
            //continue normal way
        }
        else
        {
            //show dialog
        }


    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this@OverviewActivity, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


}