package com.example.dairy

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class CustomerAdapter(ctx: Context, items: List<Customer>) : ArrayAdapter<Customer>(ctx, 0, items) {
    private fun bind(pos: Int, convert: View?, parent: ViewGroup): View {
        val v = convert ?: LayoutInflater.from(context).inflate(R.layout.item_customer, parent, false)
        val c = getItem(pos)!!
        Fonts.apply(v.findViewById<TextView>(R.id.tvName), context, c.name, 17f)
        v.findViewById<TextView>(R.id.tvMeta).text =
            "(${c.id})" + if (c.mobile.isNotEmpty()) "   ${c.mobile}" else ""
        return v
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup) = bind(position, convertView, parent)
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup) = bind(position, convertView, parent)
}

class EntryAdapter(ctx: Context, items: List<Record>) : ArrayAdapter<Record>(ctx, 0, items) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_entry, parent, false)
        val r = getItem(position)!!
        Fonts.apply(v.findViewById<TextView>(R.id.tvName), context, r.name, 17f)
        v.findViewById<TextView>(R.id.tvMeta).text = "(${r.id})   ${r.date}   ${r.sessionMr}   ${r.typeName}"
        v.findViewById<TextView>(R.id.tvDetail).text = context.fmt(
            R.string.entry_detail, r.liter, r.fat, r.snf, r.degree, r.rate, r.total
        )
        return v
    }
}
