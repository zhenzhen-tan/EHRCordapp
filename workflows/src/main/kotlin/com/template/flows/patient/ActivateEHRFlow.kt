package com.template.flows.patient

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.EHRShareAgreementContract
import com.template.states.EHRShareAgreementState
import com.template.states.EHRShareAgreementStateStatus
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@StartableByRPC
@InitiatingFlow
open class ActivateEHRFlow(val EHR: StateAndRef<EHRShareAgreementState>) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        // create EHR activation transaction
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val builder = TransactionBuilder(notary)
                .addInputState(EHR)
                .addOutputState(EHR.state.data.copy(status = EHRShareAgreementStateStatus.ACTIVE), EHRShareAgreementContract.EHR_CONTRACT_ID)
                .addCommand(EHRShareAgreementContract.Commands.Activate(), ourIdentity.owningKey)
        builder.verify(serviceHub)
        val selfSignedTx = serviceHub.signInitialTransaction(builder)

        val originDoctorSession = initiateFlow(EHR.state.data.originDoctor)
        subFlow(FinalityFlow(selfSignedTx, listOf(originDoctorSession)))

        // We should notify origin doctor about changes with the ACTIVATED EHR
        subFlow(NotifyOriginDoctorFlow("EHR approved by patient and is now active", EHR.state.data.originDoctor))

        return selfSignedTx
    }
}