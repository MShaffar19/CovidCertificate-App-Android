/*
 * Copyright (c) 2021 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package ch.admin.bag.covidcertificate.wallet.util

import android.content.Context
import android.text.SpannableString
import androidx.annotation.ColorRes
import ch.admin.bag.covidcertificate.common.util.addBoldDate
import ch.admin.bag.covidcertificate.common.util.makeSubStringBold
import ch.admin.bag.covidcertificate.sdk.core.data.ErrorCodes
import ch.admin.bag.covidcertificate.sdk.core.models.state.*
import ch.admin.bag.covidcertificate.wallet.R

const val DATE_REPLACEMENT_STRING = "{DATE}"

/**
 * The verification state indicates an offline mode if it is an ERROR and the error code is set to GENERAL_OFFLINE (G|OFF)
 */
fun VerificationState.isOfflineMode() = this is VerificationState.ERROR && this.error.code == ErrorCodes.GENERAL_OFFLINE
fun VerificationState.isTimeInconsistency() = this is VerificationState.ERROR && this.error.code == ErrorCodes.TIME_INCONSISTENCY

fun VerificationState.INVALID.getValidationStatusString(context: Context) = when {
	signatureState is CheckSignatureState.INVALID -> {
		val invalidSignatureState = signatureState as CheckSignatureState.INVALID
		if (invalidSignatureState.signatureErrorCode == ErrorCodes.SIGNATURE_TYPE_INVALID) {
			context.getString(R.string.wallet_error_invalid_format)
				.makeSubStringBold(context.getString(R.string.wallet_error_invalid_format_bold))
		} else {
			context.getString(R.string.wallet_error_invalid_signature)
				.makeSubStringBold(context.getString(R.string.wallet_error_invalid_signature_bold))
		}
	}
	revocationState is CheckRevocationState.INVALID -> {
		context.getString(R.string.wallet_error_revocation)
			.makeSubStringBold(context.getString(R.string.wallet_error_revocation_bold))
	}
	nationalRulesState is CheckNationalRulesState.NOT_VALID_ANYMORE -> {
		context.getString(R.string.wallet_error_expired)
			.makeSubStringBold(context.getString(R.string.wallet_error_expired_bold))
	}
	nationalRulesState is CheckNationalRulesState.NOT_YET_VALID -> {
		context.getString(R.string.wallet_error_valid_from).addBoldDate(
			DATE_REPLACEMENT_STRING,
			(nationalRulesState as CheckNationalRulesState.NOT_YET_VALID).validityRange?.validFrom!!
		)
	}
	nationalRulesState is CheckNationalRulesState.INVALID -> {
		SpannableString(context.getString(R.string.wallet_error_national_rules))
	}
	else -> SpannableString(context.getString(R.string.unknown_error))
}

@ColorRes
fun VerificationState.getNameDobColor(): Int {
	return when (this) {
		is VerificationState.INVALID -> R.color.grey
		else -> R.color.black
	}
}

/**
 * @return True if this is a successful (wallet) verification state and the certificate is only valid in switzerland
 */
fun VerificationState.isValidOnlyInSwitzerland(): Boolean {
	return when (this) {
		is VerificationState.SUCCESS -> {
			val walletSuccessState = this.successState as? SuccessState.WalletSuccessState
			walletSuccessState?.isValidOnlyInSwitzerland ?: false
		}
		is VerificationState.INVALID -> {
			val nationalRulesState = this.nationalRulesState
			when (nationalRulesState) {
				is CheckNationalRulesState.NOT_YET_VALID -> nationalRulesState.isOnlyValidInCH
				is CheckNationalRulesState.NOT_VALID_ANYMORE -> nationalRulesState.isOnlyValidInCH
				is CheckNationalRulesState.INVALID -> nationalRulesState.isOnlyValidInCH
				else -> false
			}
		}
		else -> false
	}
}

/**
 * @return True if this is an invalid verification state and only the national rules check failed (signature and revocation must be valid)
 */
fun VerificationState.isOnlyNationalRulesInvalid(): Boolean {
	return when (this) {
		is VerificationState.INVALID -> signatureState is CheckSignatureState.SUCCESS
				&& (revocationState is CheckRevocationState.SUCCESS || revocationState is CheckRevocationState.SKIPPED)
		else -> false
	}
}

fun VerificationState.getInvalidQrCodeAlpha(isTestCertificate: Boolean): Float {
	return when (this) {
		is VerificationState.INVALID -> if (!isTestCertificate && isOnlyNationalRulesInvalid()) 1f else 0.55f
		else -> 1f
	}
}

fun VerificationState.getInvalidContentAlpha(): Float {
	return when (this) {
		is VerificationState.INVALID -> 0.55f
		else -> 1f
	}
}
