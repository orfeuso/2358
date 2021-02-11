package com.project.ti2358.ui.strategyTazik

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.manager.StrategyTazik
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class StrategyTazikStartFragment : Fragment() {

    val strategyTazik: StrategyTazik by inject()
    var adapterList: ItemTazikRecyclerViewAdapter = ItemTazikRecyclerViewAdapter(emptyList())
    lateinit var searchView: SearchView
    lateinit var stocks: MutableList<Stock>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tazik_start, container, false)
        val list = view.findViewById<RecyclerView>(R.id.list)

        list.addItemDecoration(
            DividerItemDecoration(
                list.context,
                DividerItemDecoration.VERTICAL
            )
        )

        if (list is RecyclerView) {
            with(list) {
                layoutManager = LinearLayoutManager(context)
                adapter = adapterList
            }
        }

        val buttonStart = view.findViewById<Button>(R.id.buttonStart)
        buttonStart.setOnClickListener {
            if (strategyTazik.stocksSelected.isNotEmpty()) {
                view.findNavController().navigate(R.id.action_nav_tazik_start_to_nav_tazik_finish)
            } else {
                Utils.showErrorAlert(requireContext())
            }
        }

        var sort = Sorting.DESCENDING
        val buttonUpdate = view.findViewById<Button>(R.id.buttonUpdate)
        buttonUpdate.setOnClickListener {
            strategyTazik.process()
            stocks = strategyTazik.resort(sort)
            adapterList.setData(stocks)
            sort = if (sort == Sorting.DESCENDING) {
                Sorting.ASCENDING
            } else {
                Sorting.DESCENDING
            }
        }

        strategyTazik.process()
        stocks = strategyTazik.resort(sort)
        adapterList.setData(stocks)

        searchView = view.findViewById(R.id.searchView)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                processText(query)
                return false
            }
            override fun onQueryTextChange(newText: String): Boolean {
                processText(newText)
                return false
            }

            fun processText(text: String) {
                strategyTazik.process()
                stocks = strategyTazik.resort(sort)

                if (text.isNotEmpty()) {
                    stocks = stocks.filter {
                        it.marketInstrument.ticker.contains(text, ignoreCase = true)
                    } as MutableList<Stock>
                }
                adapterList.setData(stocks)
            }
        })

        searchView.setOnCloseListener {
            stocks = strategyTazik.process()
            adapterList.setData(stocks)
            false
        }

        return view
    }

    inner class ItemTazikRecyclerViewAdapter(
        private var values: List<Stock>
    ) : RecyclerView.Adapter<ItemTazikRecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(
                R.layout.fragment_tazik_start_item,
                parent,
                false
            )

            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.stock = item

            holder.checkBoxView.setOnCheckedChangeListener(null)
            holder.checkBoxView.isChecked = strategyTazik.isSelected(item)

            holder.tickerView.text = "${position}. ${item.marketInstrument.ticker}"
            holder.priceView.text = "${item.getPrice2359String()} -> ${item.getPriceString()}"

            val volume = item.getTodayVolume() / 1000f
            holder.volumeTodayView.text = "%.1fk".format(volume)

            val volumeCash = item.dayVolumeCash / 1000f / 1000f
            holder.volumeTodayCashView.text = "%.2f B$".format(volumeCash)

            holder.changePriceAbsoluteView.text = item.changePrice2359DayAbsolute.toDollar()
            holder.changePricePercentView.text = item.changePrice2359DayPercent.toPercent()

            if (item.changePrice2359DayAbsolute < 0) {
                holder.changePriceAbsoluteView.setTextColor(Utils.RED)
                holder.changePricePercentView.setTextColor(Utils.RED)
            } else {
                holder.changePriceAbsoluteView.setTextColor(Utils.GREEN)
                holder.changePricePercentView.setTextColor(Utils.GREEN)
            }

            holder.checkBoxView.setOnCheckedChangeListener { _, isChecked ->
                strategyTazik.setSelected(holder.stock, !isChecked)
            }

            holder.itemView.setOnClickListener {
                Utils.openTinkoffForTicker(requireContext(), holder.stock.marketInstrument.ticker)
            }
        }

        override fun getItemCount(): Int = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            lateinit var stock: Stock

            val tickerView: TextView = view.findViewById(R.id.stock_item_ticker)
            val priceView: TextView = view.findViewById(R.id.stock_item_price)

            val volumeTodayView: TextView = view.findViewById(R.id.stock_item_volume_today)
            val volumeTodayCashView: TextView = view.findViewById(R.id.stock_item_volume_today_cash)

            val changePriceAbsoluteView: TextView = view.findViewById(R.id.stock_item_price_change_absolute)
            val changePricePercentView: TextView = view.findViewById(R.id.stock_item_price_change_percent)

            val checkBoxView: CheckBox = view.findViewById(R.id.check_box)
        }
    }
}