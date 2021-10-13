/*
 * Copyright (c) 2021 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package ch.admin.bag.covidcertificate.verifier

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import ch.admin.bag.covidcertificate.common.config.ConfigViewModel
import ch.admin.bag.covidcertificate.common.config.InfoBoxModel
import ch.admin.bag.covidcertificate.common.data.ConfigSecureStorage
import ch.admin.bag.covidcertificate.common.debug.DebugFragment
import ch.admin.bag.covidcertificate.common.dialog.InfoDialogFragment
import ch.admin.bag.covidcertificate.common.html.BuildInfo
import ch.admin.bag.covidcertificate.common.html.ImprintFragment
import ch.admin.bag.covidcertificate.sdk.android.CovidCertificateSdk
import ch.admin.bag.covidcertificate.sdk.android.models.VerifierCertificateHolder
import ch.admin.bag.covidcertificate.sdk.android.verification.state.VerifierDecodeState
import ch.admin.bag.covidcertificate.verifier.data.VerifierSecureStorage
import ch.admin.bag.covidcertificate.verifier.databinding.FragmentHomeBinding
import ch.admin.bag.covidcertificate.verifier.faq.VerifierFaqFragment
import ch.admin.bag.covidcertificate.verifier.pager.HomescreenPageAdapter
import ch.admin.bag.covidcertificate.verifier.pager.HomescreenPagerFragment
import ch.admin.bag.covidcertificate.verifier.qr.VerifierQrScanFragment
import ch.admin.bag.covidcertificate.verifier.verification.VerificationFragment
import ch.admin.bag.covidcertificate.verifier.zebra.ZebraActionBroadcastReceiver
import com.google.android.material.tabs.TabLayoutMediator
import java.util.concurrent.atomic.AtomicLong

class HomeFragment : Fragment() {

	companion object {
		fun newInstance(): HomeFragment {
			return HomeFragment()
		}
	}

	private val configViewModel by activityViewModels<ConfigViewModel>()
	private val zebraBroadcastReceiver by lazy { ZebraActionBroadcastReceiver(VerifierSecureStorage.getInstance(requireContext())) }

	private var _binding: FragmentHomeBinding? = null
	private val binding get() = _binding!!

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = FragmentHomeBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		val adapter = HomescreenPageAdapter(this, HomescreenPagerFragment.getDescriptions().size)
		binding.viewPager.adapter = adapter

		binding.homescreenScanButton.setOnClickListener {
			parentFragmentManager.beginTransaction()
				.setCustomAnimations(R.anim.slide_enter, R.anim.slide_exit, R.anim.slide_pop_enter, R.anim.slide_pop_exit)
				.replace(R.id.fragment_container, VerifierQrScanFragment.newInstance())
				.addToBackStack(VerifierQrScanFragment::class.java.canonicalName)
				.commit()
		}

		binding.homescreenSupportButton.setOnClickListener {
			parentFragmentManager.beginTransaction()
				.setCustomAnimations(R.anim.slide_enter, R.anim.slide_exit, R.anim.slide_pop_enter, R.anim.slide_pop_exit)
				.replace(R.id.fragment_container, VerifierFaqFragment.newInstance())
				.addToBackStack(VerifierFaqFragment::class.java.canonicalName)
				.commit()
		}

		TabLayoutMediator(binding.tabLayout, binding.viewPager) { _, _ ->
			//Some implementation
		}.attach()

		if (DebugFragment.EXISTS) {
			setupDebugFragment()
		}

		binding.homescreenHeader.headerImpressum.setOnClickListener {
			val buildInfo = BuildInfo(
				getString(R.string.verifier_app_title),
				BuildConfig.VERSION_NAME,
				BuildConfig.BUILD_TIME,
				BuildConfig.FLAVOR,
				getString(R.string.verifier_terms_privacy_link),
				"covidCheck",
			)
			parentFragmentManager.beginTransaction()
				.setCustomAnimations(R.anim.slide_enter, R.anim.slide_exit, R.anim.slide_pop_enter, R.anim.slide_pop_exit)
				.replace(
					R.id.fragment_container, ImprintFragment.newInstance(
						R.string.impressum_title,
						buildInfo
					)
				)
				.addToBackStack(ImprintFragment::class.java.canonicalName)
				.commit()
		}

		setupInfoBox()

		zebraBroadcastReceiver.registerWith(requireContext()) { decodeQrCodeData(it) }
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
		zebraBroadcastReceiver.unregisterWith(requireContext())
	}

	private fun setupDebugFragment() {
		val lastClick = AtomicLong(0)
		val debugButtonClickListener = View.OnClickListener {
			val now = System.currentTimeMillis()
			if (lastClick.get() > now - 1000L) {
				lastClick.set(0)
				parentFragmentManager.beginTransaction()
					.setCustomAnimations(R.anim.slide_enter, R.anim.slide_exit, R.anim.slide_pop_enter, R.anim.slide_pop_exit)
					.replace(R.id.fragment_container, DebugFragment.newInstance())
					.addToBackStack(DebugFragment::class.java.canonicalName)
					.commit()
			} else {
				lastClick.set(now)
			}
		}
		binding.homescreenHeader.schwiizerchruez.setOnClickListener(debugButtonClickListener)
	}

	private fun setupInfoBox() {
		configViewModel.configLiveData.observe(viewLifecycleOwner) { config ->
			val notificationButton = binding.homescreenHeader.headerNotification
			val localizedInfo = config.getInfoBox(getString(R.string.language_key))
			val hasInfoBox = localizedInfo != null

			val onClickListener = localizedInfo?.let { infoBox ->
				val secureStorage = ConfigSecureStorage.getInstance(notificationButton.context)
				if (secureStorage.getLastShownInfoBoxId() != infoBox.infoId) {
					closeCurrentInfoDialog()
					showInfoDialog(infoBox)
					secureStorage.setLastShownInfoBoxId(infoBox.infoId)
				}

				return@let View.OnClickListener {
					closeCurrentInfoDialog()
					showInfoDialog(infoBox)
					secureStorage.setLastShownInfoBoxId(infoBox.infoId)
				}
			}

			notificationButton.isVisible = hasInfoBox
			notificationButton.setOnClickListener(onClickListener)
		}
	}

	private fun closeCurrentInfoDialog() {
		(childFragmentManager.findFragmentByTag(InfoDialogFragment::class.java.canonicalName) as? InfoDialogFragment)?.dismiss()
	}

	private fun showInfoDialog(infoBox: InfoBoxModel) {
		InfoDialogFragment.newInstance(infoBox).show(childFragmentManager, InfoDialogFragment::class.java.canonicalName)
	}

	private fun decodeQrCodeData(qrCodeData: String) {
		when (val decodeState = CovidCertificateSdk.Verifier.decode(qrCodeData)) {
			is VerifierDecodeState.SUCCESS -> {
				showVerificationFragment(decodeState.certificateHolder)
			}
			is VerifierDecodeState.ERROR -> {
				// Ignore errors when scanning in the home screen
			}
		}
	}

	private fun showVerificationFragment(certificateHolder: VerifierCertificateHolder) {
		parentFragmentManager.beginTransaction()
			.setCustomAnimations(R.anim.slide_enter, R.anim.slide_exit, R.anim.slide_pop_enter, R.anim.slide_pop_exit)
			.replace(R.id.fragment_container, VerificationFragment.newInstance(certificateHolder))
			.addToBackStack(VerificationFragment::class.java.canonicalName)
			.commit()
	}

}