package com.fenix.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fenix.app.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val client = OkHttpClient()
    private lateinit var adapter: ThingsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ThingsAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.refreshButton.setOnClickListener {
            fetchThings()
        }

        fetchThings()
    }

    private fun fetchThings() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("https://jsonplaceholder.typicode.com/posts")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")

                    val jsonArray = JSONArray(response.body?.string())
                    val thingsList = mutableListOf<String>()

                    for (i in 0 until jsonArray.length()) {
                        val jsonObject: JSONObject = jsonArray.getJSONObject(i)
                        thingsList.add(jsonObject.getString("title"))
                    }

                    withContext(Dispatchers.Main) {
                        adapter.updateData(thingsList)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    inner class ThingsAdapter : RecyclerView.Adapter<ThingsAdapter.ThingViewHolder>() {

        private var thingsList: List<String> = emptyList()

        inner class ThingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textView: TextView = itemView.findViewById(R.id.textView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThingViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_thing, parent, false)
            return ThingViewHolder(view)
        }

        override fun onBindViewHolder(holder: ThingViewHolder, position: Int) {
            holder.textView.text = thingsList[position]
        }

        override fun getItemCount(): Int = thingsList.size

        fun updateData(newData: List<String>) {
            thingsList = newData
            notifyDataSetChanged()
        }
    }
}