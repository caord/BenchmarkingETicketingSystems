package uk.co.pervasive_intelligence.dice.protocol.pplast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.List;


import it.unisa.dia.gas.jpbc.Element;
import uk.co.pervasive_intelligence.dice.Crypto;
import uk.co.pervasive_intelligence.dice.Crypto.BigIntEuclidean;
import uk.co.pervasive_intelligence.dice.protocol.NFCAndroidCommand;
import uk.co.pervasive_intelligence.dice.protocol.NFCAndroidSharedMemory;
import uk.co.pervasive_intelligence.dice.protocol.NFCAndroidState;
import uk.co.pervasive_intelligence.dice.protocol.data.ListData;
import uk.co.pervasive_intelligence.dice.protocol.pplast.PPLASTSharedMemory.Actor;
import uk.co.pervasive_intelligence.dice.protocol.pplast.data.TicketDetails;
import uk.co.pervasive_intelligence.dice.protocol.pplast.data.UserData;
import uk.co.pervasive_intelligence.dice.state.Action;
import uk.co.pervasive_intelligence.dice.state.Message;

/**
 * The issuing states for PPLAST.
 * <p>
 * (c) Steve Wesemeyer 2017
 */

public class PPLASTIssuingStates {

  /**
   * Logback logger.
   */
  private static final Logger LOG = LoggerFactory.getLogger(PPLASTIssuingStates.class);

  /**
   * State 04.
   * As User: generate the ticket request
   */
  public static class IState04 extends NFCAndroidState {


    private byte[] generateTicketRequest() {
      final PPLASTSharedMemory sharedMemory = (PPLASTSharedMemory) this.getSharedMemory();
      final UserData userData = (UserData) sharedMemory.getData(Actor.USER);
      final Crypto crypto = Crypto.getInstance();

      // get some element from sharedMemory
      final BigInteger p = sharedMemory.p;
      final Element Y_P = sharedMemory.getPublicKey(Actor.POLICE).getImmutable();
      final Element xi = sharedMemory.xi.getImmutable();
      final Element g = sharedMemory.g.getImmutable();
      final Element h = sharedMemory.h.getImmutable();

      final int numberOfVerifiers = userData.VerifierList.length;

      // compute some stuff for the ZKP PI_1_U
      final Element B_U = g.add(h.mul(userData.r_U)).add(userData.Y_U);
      final BigInteger v_1 = crypto.secureRandom(p);
      final BigInteger v_2 = crypto.secureRandom(p);
      final BigInteger z_U = crypto.secureRandom(p);
      // store z_U for later use...
      userData.z_U = z_U;

      // final BigInteger r_dash_U = crypto.secureRandom(p);
      final BigInteger x_dash_U = crypto.secureRandom(p);
      final BigInteger e_dash_U = crypto.secureRandom(p);
      final BigInteger v_dash_2 = crypto.secureRandom(p);
      final BigInteger v_dash_3 = crypto.secureRandom(p);
      final BigInteger v_dash = crypto.secureRandom(p);
      final BigInteger[] z_dash = new BigInteger[numberOfVerifiers];
      for (int i = 0; i < numberOfVerifiers; i++) {
        z_dash[i] = crypto.secureRandom(p);
      }
      BigIntEuclidean gcd = BigIntEuclidean.calculate(v_1, p);
      final BigInteger v_3 = gcd.x.mod(p);
      final BigInteger v = (userData.r_U.subtract(v_2.multiply(v_3))).mod(p);
      final Element sigma_bar_U = userData.sigma_U.mul(v_1).getImmutable();
      final Element sigma_tilde_U = (sigma_bar_U.mul(userData.e_U.negate().mod(p))).add(B_U.mul(v_1)).getImmutable();
      final Element B_bar_U = B_U.mul(v_1).add(sharedMemory.h.mul(v_2.negate().mod(p))).getImmutable();
      final Element W_1 = ((sigma_bar_U.mul(e_dash_U.negate().mod(p))).add(h.mul(v_dash_2))).getImmutable();
      final Element W_2 = (((B_bar_U.mul(v_dash_3.negate().mod(p))).add(xi.mul(x_dash_U))).add(h.mul(v_dash))).getImmutable();

      final byte[][] z_V = new byte[numberOfVerifiers][];
      final Element[] P_V = new Element[numberOfVerifiers];
      final Element[] P_dash_V = new Element[numberOfVerifiers];
      final Element[] Q_V = new Element[numberOfVerifiers];
      final Element[] Q_dash_V = new Element[numberOfVerifiers];

      for (int i = 0; i < numberOfVerifiers; i++) {
        final ListData zvData = new ListData(Arrays.asList(z_U.toByteArray(), userData.VerifierList[i].getBytes()));
        z_V[i] = crypto.getHash(zvData.toBytes(), sharedMemory.Hash1);
        final BigInteger z_Vnum = (new BigInteger(1, z_V[i])).mod(sharedMemory.p);
        P_V[i] = userData.Y_U.add(Y_P.mul(z_Vnum)).getImmutable();
        P_dash_V[i] = ((xi.mul(x_dash_U)).add(Y_P.mul(z_dash[i]))).getImmutable();
        Q_V[i] = xi.mul(z_Vnum).getImmutable();
        Q_dash_V[i] = xi.mul(z_dash[i]).getImmutable();
      }

      final List<byte[]> c_DataList = new ArrayList<>();


      c_DataList
              .addAll(Arrays.asList(sigma_bar_U.toBytes(), sigma_tilde_U.toBytes(), B_bar_U.toBytes(), W_1.toBytes(), W_2.toBytes()));
      for (int i = 0; i < numberOfVerifiers; i++) {
        c_DataList.add(P_V[i].toBytes());
        c_DataList.add(P_dash_V[i].toBytes());
        c_DataList.add(Q_V[i].toBytes());
        c_DataList.add(Q_dash_V[i].toBytes());
      }
      final byte[] c_hash = crypto.getHash((new ListData(c_DataList)).toBytes(), sharedMemory.Hash1);
      final BigInteger c_hashNum = (new BigInteger(1, c_hash)).mod(p);

      final BigInteger e_hat_U = (e_dash_U.subtract(c_hashNum.multiply(userData.e_U))).mod(p);
      final BigInteger v_hat_2 = (v_dash_2.subtract(c_hashNum.multiply(v_2))).mod(p);
      final BigInteger v_hat_3 = (v_dash_3.subtract(c_hashNum.multiply(v_3))).mod(p);
      final BigInteger v_hat = (v_dash.subtract(c_hashNum.multiply(v))).mod(p);
      final BigInteger x_hat_U = (x_dash_U.subtract(c_hashNum.multiply(userData.x_U))).mod(p);

      final BigInteger[] z_hat_V = new BigInteger[numberOfVerifiers];
      for (int i = 0; i < numberOfVerifiers; i++) {
        final BigInteger z_VNum = (new BigInteger(1, z_V[i])).mod(p);
        z_hat_V[i] = (z_dash[i].subtract(c_hashNum.multiply(z_VNum))).mod(p);
      }

      final List<byte[]> sendDataList = new ArrayList<>();
      sendDataList
              .addAll(Arrays.asList(sigma_bar_U.toBytes(), sigma_tilde_U.toBytes(), B_bar_U.toBytes(), W_1.toBytes(), W_2.toBytes()));

      // need to send all the verifier IDs
      sendDataList.add(BigInteger.valueOf(numberOfVerifiers).toByteArray());
      for (int i = 0; i < numberOfVerifiers; i++) {
        sendDataList.add(userData.VerifierList[i].getBytes(StandardCharsets.UTF_8));
      }

      // send the Ps and Qs
      for (int i = 0; i < numberOfVerifiers; i++) {
        sendDataList.add(P_V[i].toBytes());
        sendDataList.add(P_dash_V[i].toBytes());
        sendDataList.add(Q_V[i].toBytes());
        sendDataList.add(Q_dash_V[i].toBytes());
      }

      // add the last few items...
      sendDataList.addAll(Arrays.asList(c_hash, e_hat_U.toByteArray(), v_hat_2.toByteArray(), v_hat_3.toByteArray(),
              v_hat.toByteArray(), x_hat_U.toByteArray()));

      for (int i = 0; i < numberOfVerifiers; i++) {
        sendDataList.add(z_hat_V[i].toByteArray());
      }

      final ListData sendData = new ListData(sendDataList);
      return sendData.toBytes();
    }


    /**
     * Gets the required action given a message.
     *
     * @param message The received message to process.
     * @return The required action.
     */
    @Override
    public Action<NFCAndroidCommand> getAction(Message message) {
      // We are now the user.
      ((PPLASTSharedMemory) this.getSharedMemory()).actAs(PPLASTSharedMemory.Actor.USER);

      if (message.getType() == Message.Type.DATA) {
        // Send back the user identity data.
        if (message.getData() == null) {
          byte[] data = this.generateTicketRequest();

          if (data != null) {
            LOG.debug("generate user ticket request complete");
            byte[] response = this.addResponseCode(data, NFCAndroidSharedMemory.RESPONSE_OK);
            return new Action<>(Action.Status.END_SUCCESS, 5, NFCAndroidCommand.RESPONSE,
                    response, 0);
          }
        }
      }

      return super.getAction(message);
    }

  }

  /**
   * State 05.
   * As User: verify the ticket details send by the seller
   */
  public static class IState05 extends NFCAndroidState {


    private boolean verifyTicketDetails(byte[] data) {
      final PPLASTSharedMemory sharedMemory = (PPLASTSharedMemory) this.getSharedMemory();
      final UserData userData = (UserData) sharedMemory.getData(Actor.USER);
      final Crypto crypto = Crypto.getInstance();

      // Decode the received data.
      final ListData listData = ListData.fromBytes(data);
      if (listData.getList().size() <= 0) { // dependent on the number of verifiers...
        LOG.error("wrong number of data elements: " + listData.getList().size());
        return false;
      }
      int indx = 0;
      final Element C_U = sharedMemory.curveG1ElementFromBytes(listData.getList().get(indx++));
      // final String ticketText = new String(listData.getList().get(indx++), StandardCharsets.UTF_8);
      final int numOfVerifiers = new BigInteger(1, listData.getList().get(indx++)).intValue();

      final TicketDetails ticketDetails = new TicketDetails(numOfVerifiers);
      indx = ticketDetails.populateTicketDetails(sharedMemory, listData, indx);

      // verify ticket details
      //TODO: make this a flag:

      if (1 == 0) {
        for (int i = 0; i < numOfVerifiers; i++) {
          // final Element Y_V = sharedMemory.getPublicKey(ticketDetails.VerifierList[i]);
          final byte[] verifyD_V = crypto.getHash(
                  (new ListData(Arrays.asList(C_U.toBytes(), ticketDetails.VerifierList[i].getBytes()))).toBytes(), sharedMemory.Hash2);
          if (!Arrays.equals(ticketDetails.D_V[i], verifyD_V)) {
            LOG.error("failed to verify D_V[" + i + "] for verifier: " + ticketDetails.VerifierList[i]);
            return false;
          }
        }
        LOG.debug("Passed D_V verification!");
        for (int i = 0; i < numOfVerifiers; i++) {
          final byte[] verifys_V = crypto.getHash(
                  (new ListData(
                          Arrays.asList(ticketDetails.P_V[i].toBytes(), ticketDetails.Q_V[i].toBytes(), ticketDetails.E_V[i].toBytes(),
                                  ticketDetails.F_V[i].toBytes(), ticketDetails.K_V[i].toBytes(), ticketDetails.ticketText.getBytes()))).toBytes(),
                  sharedMemory.Hash1);
          if (!Arrays.equals(ticketDetails.s_V[i], verifys_V)) {
            LOG.error("failed to verify s_V[" + i + "] for verifier: " + ticketDetails.VerifierList[i]);
            return false;
          }

        }
        LOG.debug("Passed s_V verification!");

        // some elements from sharedMemory
        final Element Y_bar_S = sharedMemory.getPublicKey(Actor.SELLER).getImmutable();
        final Element g = sharedMemory.g.getImmutable();
        final Element g_frak = sharedMemory.g_frak.getImmutable();
        final Element h = sharedMemory.h.getImmutable();
        final Element h_tilde = sharedMemory.h_tilde.getImmutable();
        final BigInteger p = sharedMemory.p;

        for (int i = 0; i < numOfVerifiers; i++) {
          LOG.debug("Verifier: " + i + " is being checked.");

          final Element lhs = (sharedMemory.pairing.pairing(ticketDetails.sigma_V[i], Y_bar_S.add(g_frak.mul(ticketDetails.e_V[i]))))
                  .getImmutable();

          LOG.debug(System.currentTimeMillis() + " computed lhs: " + lhs);
          final BigInteger s_Vnum = (new BigInteger(1, ticketDetails.s_V[i])).mod(p);

          final Element rhs = (sharedMemory.pairing.pairing((g.add(h.mul(ticketDetails.w_V[i]))).add(h_tilde.mul(s_Vnum)), g_frak))
                  .getImmutable();
          LOG.debug(System.currentTimeMillis() + " computed rhs: " + rhs);

          if (!lhs.isEqual(rhs)) {
            LOG.error("failed to verify pairing check [" + i + "] for verifier: " + ticketDetails.VerifierList[i]);
            return false;
          }
        }
        LOG.debug("Passed sigma_V pairing verification!");

        final List<byte[]> verifys_PData = new ArrayList<>();
        for (int i = 0; i < numOfVerifiers; i++) {
          verifys_PData.add(ticketDetails.s_V[i]);
        }

        if (!Arrays.equals(ticketDetails.s_P, crypto.getHash((new ListData(verifys_PData)).toBytes(), sharedMemory.Hash1))) {
          LOG.error("failed to verify s_P hash");
          return false;
        }

        LOG.debug("Passed s_P verification!");

        final BigInteger s_PNum = (new BigInteger(1, ticketDetails.s_P)).mod(p);

        final Element lhs = (sharedMemory.pairing.pairing(ticketDetails.sigma_P, Y_bar_S.add(g_frak.mul(ticketDetails.e_P))))
                .getImmutable();
        final Element rhs = (sharedMemory.pairing.pairing(g.add(h.mul(ticketDetails.w_P)).add(h_tilde.mul(s_PNum)), g_frak))
                .getImmutable();

        if (!lhs.isEqual(rhs)) {
          LOG.error("failed to verify sigma_P pairing check");
          return false;
        }

        LOG.debug("Passed sigma_P pairing verification!");
      }
      // store the ticket details
      // note that z_U was stored during the ticket request generation
      userData.C_U = C_U;
      userData.ticketDetails = ticketDetails;

      return true;
    }


    /**
     * Gets the required action given a message.
     *
     * @param message The received message to process.
     * @return The required action.
     */
    @Override
    public Action<NFCAndroidCommand> getAction(Message message) {
      // We are now the user.
      ((PPLASTSharedMemory) this.getSharedMemory()).actAs(PPLASTSharedMemory.Actor.USER);

      if (message.getType() == Message.Type.DATA) {
        // Send back the user identity data.
        if (message.getData() != null) {
          if (this.verifyTicketDetails(message.getData())) {
            LOG.debug("successfully obtained a ticket !");
            return new Action<>(Action.Status.END_SUCCESS, 6, NFCAndroidCommand.RESPONSE,
                    NFCAndroidSharedMemory.RESPONSE_OK, 0);
          }
        }
      }

      return super.getAction(message);
    }

  }


}


