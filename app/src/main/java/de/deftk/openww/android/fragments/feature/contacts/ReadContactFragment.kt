package de.deftk.openww.android.fragments.feature.contacts

import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import de.deftk.openww.android.R
import de.deftk.openww.android.adapter.recycler.ContactDetailAdapter
import de.deftk.openww.android.api.Response
import de.deftk.openww.android.databinding.FragmentReadContactBinding
import de.deftk.openww.android.utils.ContactUtil
import de.deftk.openww.android.utils.Reporter
import de.deftk.openww.android.viewmodel.ContactsViewModel
import de.deftk.openww.android.viewmodel.UserViewModel
import de.deftk.openww.api.model.Feature
import de.deftk.openww.api.model.IOperatingScope
import de.deftk.openww.api.model.Permission
import de.deftk.openww.api.model.feature.contacts.IContact

class ReadContactFragment : Fragment() {

    private val args: ReadContactFragmentArgs by navArgs()
    private val userViewModel: UserViewModel by activityViewModels()
    private val contactsViewModel: ContactsViewModel by activityViewModels()
    private val navController by lazy { findNavController() }
    private val adapter = ContactDetailAdapter(true, null)

    private lateinit var binding: FragmentReadContactBinding
    private lateinit var contact: IContact

    private var scope: IOperatingScope? = null
    private var deleted = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentReadContactBinding.inflate(inflater, container, false)
        (requireActivity() as AppCompatActivity).supportActionBar?.show()

        binding.contactDetailList.adapter = adapter
        binding.contactDetailList.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))

        contactsViewModel.deleteResponse.observe(viewLifecycleOwner) { response ->
            if (response != null)
                contactsViewModel.resetDeleteResponse()

            if (response is Response.Success) {
                deleted = true
                navController.popBackStack()
            } else if (response is Response.Failure) {
                Reporter.reportException(R.string.error_delete_failed, response.exception, requireContext())
            }
        }

        binding.fabEditContact.setOnClickListener {
            navController.navigate(ReadContactFragmentDirections.actionReadContactFragmentToEditContactFragment(scope!!.login, contact.id.toString(), getString(R.string.edit_contact)))
        }

        userViewModel.apiContext.observe(viewLifecycleOwner) { apiContext ->
            if (apiContext != null) {
                val foundScope = userViewModel.apiContext.value?.findOperatingScope(args.scope)
                if (foundScope == null) {
                    Reporter.reportException(R.string.error_scope_not_found, args.scope, requireContext())
                    navController.popBackStack()
                    return@observe
                }
                if (!Feature.ADDRESSES.isAvailable(foundScope.effectiveRights)) {
                    Reporter.reportFeatureNotAvailable(requireContext())
                    navController.popBackStack()
                    return@observe
                }
                if (scope != null)
                    contactsViewModel.getFilteredContactsLiveData(scope!!).removeObservers(viewLifecycleOwner)
                scope = foundScope
                contactsViewModel.getFilteredContactsLiveData(scope!!).observe(viewLifecycleOwner) { response ->
                    if (deleted)
                        return@observe

                    if (response is Response.Success) {
                        val queried = response.value.firstOrNull { it.id.toString() == args.contactId }
                        if (queried != null) {
                            contact = queried
                        } else {
                            Reporter.reportException(R.string.error_contact_not_found, args.contactId, requireContext())
                            navController.popBackStack()
                            return@observe
                        }
                        adapter.submitList(ContactUtil.extractContactDetails(contact))
                    } else if (response is Response.Failure) {
                        Reporter.reportException(R.string.error_get_contacts_failed, response.exception, requireContext())
                        navController.popBackStack()
                        return@observe
                    }
                }
                contactsViewModel.loadContacts(scope!!, apiContext)
                binding.fabEditContact.isVisible = scope!!.effectiveRights.contains(Permission.ADDRESSES_WRITE) || scope!!.effectiveRights.contains(Permission.ADDRESSES_ADMIN)
            } else {
                adapter.submitList(emptyList())
                binding.fabEditContact.isVisible = false
            }
        }

        setHasOptionsMenu(true)
        return binding.root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (scope!!.effectiveRights.contains(Permission.ADDRESSES_WRITE) || scope!!.effectiveRights.contains(Permission.ADDRESSES_ADMIN))
            inflater.inflate(R.menu.simple_edit_item_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_edit -> {
                val action = ReadContactFragmentDirections.actionReadContactFragmentToEditContactFragment(scope!!.login, contact.id.toString(), getString(R.string.edit_contact))
                navController.navigate(action)
                true
            }
            R.id.menu_item_delete -> {
                val apiContext = userViewModel.apiContext.value ?: return false
                contactsViewModel.deleteContact(contact, scope!!, apiContext)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

}