import com.template.flows.Flow
import com.template.states.ResponsibilityState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IOUFlowTests {
    private lateinit var network: MockNetwork
    private lateinit var a: StartedMockNode
    private lateinit var b: StartedMockNode
    private lateinit var c: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("com.template.contracts.contract"),
                TestCordapp.findCordapp("com.template.flows.flow")
        )))
        a = network.createPartyNode()
        b = network.createPartyNode()
        c = network.createPartyNode()
        // For real nodes this happens automatically, but we have to manually register the flow for tests.
        listOf(a, b).forEach { it.registerInitiatedFlow(Flow.Acceptor::class.java) }
        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `flow rejects invalid IOUs`() {
        val flow = Flow.Initiator(-1, b.info.singleIdentity())
        val future = a.startFlow(flow)
        network.runNetwork()

        // The PaymentContract specifies that IOUs cannot have negative values.
        assertFailsWith<TransactionVerificationException> { future.getOrThrow() }
    }

    @Test
    fun `SignedTransaction returned by the flow is signed by the initiator`() {
        val flow = Flow.Initiator(1, b.info.singleIdentity())
        val future = a.startFlow(flow)
        network.runNetwork()

        val signedTx = future.getOrThrow()
        signedTx.verifySignaturesExcept(b.info.singleIdentity().owningKey)
    }

    @Test
    fun `SignedTransaction returned by the flow is signed by the acceptor`() {
        val flow = Flow.Initiator(1, b.info.singleIdentity())
        val future = a.startFlow(flow)
        network.runNetwork()

        val signedTx = future.getOrThrow()
        signedTx.verifySignaturesExcept(a.info.singleIdentity().owningKey)
    }

    @Test
    fun `flow records a transaction in both parties' transaction storages`() {
        val flow = Flow.Initiator(1, b.info.singleIdentity())
        val future = a.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in both transaction storages.
        for (node in listOf(a, b)) {
            assertEquals(signedTx, node.services.validatedTransactions.getTransaction(signedTx.id))
        }
    }

//    @Test
//    fun `recorded transaction has no inputs and a single output, the input IOU`() {
//        val iouValue = 1
//        val flow = Flow.Initiator(iouValue, b.info.singleIdentity())
//        val future = a.startFlow(flow)
//        network.runNetwork()
//        val signedTx = future.getOrThrow()
//
//        // We check the recorded transaction in both vaults.
//        for (node in listOf(a, b)) {
//            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)
//            val txOutputs = recordedTx!!.tx.outputs
//            assert(txOutputs.size == 1)
//
//            val recordedState = txOutputs[0].data as ResponsibilityState
//            assertEquals(recordedState.value, iouValue)
//            assertEquals(recordedState.lender, a.info.singleIdentity())
//            assertEquals(recordedState.borrower, b.info.singleIdentity())
//        }
//    }
//
//    @Test
//    fun `flow records the correct IOU in both parties' vaults`() {
//        val iouValue = 1
//        val flow = Flow.Initiator(1, b.info.singleIdentity())
//        val future = a.startFlow(flow)
//        network.runNetwork()
//        future.getOrThrow()
//
//        // We check the recorded IOU in both vaults.
//        for (node in listOf(a, b)) {
//            node.transaction {
//                val ious = node.services.vaultService.queryBy<ResponsibilityState>().states
//                assertEquals(1, ious.size)
//                val recordedState = ious.single().state.data
//                assertEquals(recordedState.value, iouValue)
//                assertEquals(recordedState.lender, a.info.singleIdentity())
//                assertEquals(recordedState.borrower, b.info.singleIdentity())
//            }
//        }
//    }
}