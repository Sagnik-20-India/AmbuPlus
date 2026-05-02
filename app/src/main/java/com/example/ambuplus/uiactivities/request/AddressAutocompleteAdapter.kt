package com.example.ambuplus.uiactivities.request

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.example.ambuplus.R
import com.example.ambuplus.utils.NominatimResult

class AddressAutocompleteAdapter(
    context: Context,
    private val suggestions: MutableList<NominatimResult>
) : ArrayAdapter<NominatimResult>(context, android.R.layout.simple_dropdown_item_1line, suggestions) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_dropdown_item_1line, parent, false)

        val textView = view as TextView
        val item = getItem(position)
        textView.text = item?.display_name ?: ""

        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getView(position, convertView, parent)
    }
}