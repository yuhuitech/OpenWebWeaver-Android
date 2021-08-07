package de.deftk.openww.android.fragments.feature.filestorage

import android.os.Bundle
import android.view.*
import android.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DividerItemDecoration
import de.deftk.openww.android.R
import de.deftk.openww.android.activities.getMainActivity
import de.deftk.openww.android.adapter.recycler.FileStorageAdapter
import de.deftk.openww.android.api.Response
import de.deftk.openww.android.databinding.FragmentFileStorageBinding
import de.deftk.openww.android.feature.LaunchMode
import de.deftk.openww.android.filter.FileStorageQuotaFilter
import de.deftk.openww.android.utils.ISearchProvider
import de.deftk.openww.android.utils.Reporter
import de.deftk.openww.android.viewmodel.FileStorageViewModel
import de.deftk.openww.android.viewmodel.UserViewModel

class FileStorageGroupFragment : Fragment(), ISearchProvider {

    private val userViewModel: UserViewModel by activityViewModels()
    private val fileStorageViewModel: FileStorageViewModel by activityViewModels()

    private lateinit var binding: FragmentFileStorageBinding
    private lateinit var searchView: SearchView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentFileStorageBinding.inflate(inflater, container, false)
        getMainActivity().supportActionBar?.show()
        getMainActivity().searchProvider = this

        val pasteMode = LaunchMode.getLaunchMode(requireActivity().intent) == LaunchMode.FILE_UPLOAD
        val adapter = FileStorageAdapter(pasteMode)
        binding.fileList.adapter = adapter
        binding.fileList.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        fileStorageViewModel.filteredQuotasResponse.observe(viewLifecycleOwner) { response ->
            if (response is Response.Success) {
                adapter.submitList(response.value.toList())
                binding.fileEmpty.isVisible = response.value.isEmpty()
            } else if (response is Response.Failure) {
                Reporter.reportException(R.string.error_get_quotas_failed, response.exception, requireContext())
            }
            getMainActivity().progressIndicator.isVisible = false
            binding.fileStorageSwipeRefresh.isRefreshing = false
        }

        binding.fileStorageSwipeRefresh.setOnRefreshListener {
            userViewModel.apiContext.value?.also { apiContext ->
                fileStorageViewModel.loadQuotas(apiContext)
            }
        }

        userViewModel.apiContext.observe(viewLifecycleOwner) { apiContext ->
            if (apiContext != null) {
                fileStorageViewModel.loadQuotas(apiContext)
                if (fileStorageViewModel.allQuotasResponse.value == null)
                    getMainActivity().progressIndicator.isVisible = true
            } else {
                binding.fileEmpty.isVisible = false
                adapter.submitList(emptyList())
                getMainActivity().progressIndicator.isVisible = true
            }
        }

        setHasOptionsMenu(true)
        return binding.root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        inflater.inflate(R.menu.list_filter_menu, menu)
        val searchItem = menu.findItem(R.id.filter_item_search)
        searchView = searchItem.actionView as SearchView
        searchView.setQuery(fileStorageViewModel.quotaFilter.value?.smartSearchCriteria?.value, false) // restore recent search
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val filter = FileStorageQuotaFilter()
                filter.smartSearchCriteria.value = newText
                fileStorageViewModel.quotaFilter.value = filter
                return true
            }
        })
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onSearchBackPressed(): Boolean {
        return if (searchView.isIconified) {
            false
        } else {
            searchView.isIconified = true
            searchView.setQuery(null, true)
            true
        }
    }

    override fun onDestroy() {
        getMainActivity().searchProvider = null
        super.onDestroy()
    }


}