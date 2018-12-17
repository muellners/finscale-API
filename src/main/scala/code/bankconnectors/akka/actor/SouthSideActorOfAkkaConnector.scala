package code.bankconnectors.akka.actor

import java.util.Date

import akka.actor.{Actor, ActorLogging}
import code.api.util.APIUtil
import code.bankconnectors.LocalMappedConnector._
import code.bankconnectors._
import code.bankconnectors.akka._
import code.customer.{CreditLimit, CreditRating, Customer, CustomerFaceImage}
import code.metadata.counterparties.CounterpartyTrait
import code.model.dataAccess.MappedBank
import code.model.{Bank => _, _}
import code.util.Helper.MdcLoggable
import net.liftweb.common.Box

import scala.collection.immutable.List


/**
  * This Actor acts in next way:
  */
class SouthSideActorOfAkkaConnector extends Actor with ActorLogging with MdcLoggable {

  def receive: Receive = waitingForRequest

  private def waitingForRequest: Receive = {
    case OutboundGetAdapterInfo(_, cc) =>
      val result = 
        InboundAdapterInfo(
          "systemName",
          "version", 
          APIUtil.gitCommit, 
          (new Date()).toString,
          cc
        )
      sender ! result   
    
    case OutboundGetBanks(cc) =>
      val result: Box[List[MappedBank]] = getBanks(None).map(r => r._1)
      sender ! InboundGetBanks(result.map(l => l.map(Transformer.bank(_))).toOption, cc)
    
    case OutboundGetBank(bankId, cc) =>
      val result: Box[MappedBank] = getBank(BankId(bankId), None).map(r => r._1)
      sender ! InboundGetBank(result.map(Transformer.bank(_)).toOption, cc)  
      
    case OutboundCheckBankAccountExists(bankId, accountId, cc) =>
      val result: Box[BankAccount] = checkBankAccountExists(BankId(bankId), AccountId(accountId), None).map(r => r._1)
      sender ! InboundCheckBankAccountExists(result.map(Transformer.bankAccount(_)).toOption, cc)
      
    case OutboundGetAccount(bankId, accountId, cc) =>
      val result: Box[BankAccount] = getBankAccount(BankId(bankId), AccountId(accountId), None).map(r => r._1)
      org.scalameta.logger.elem(result)
      sender ! InboundGetAccount(result.map(Transformer.bankAccount(_)).toOption, cc)
      
    case OutboundGetCoreBankAccounts(bankIdAccountIds, cc) =>
      val result: Box[List[CoreAccount]] = getCoreBankAccounts(bankIdAccountIds, None).map(r => r._1)
      sender ! InboundGetCoreBankAccounts(result.getOrElse(Nil).map(Transformer.coreAccount(_)), cc)
       
    case OutboundGetCustomersByUserId(userId, cc) =>
      val result: Box[List[Customer]] = getCustomersByUserId(userId, None).map(r => r._1)
      sender ! InboundGetCustomersByUserId(result.getOrElse(Nil).map(Transformer.toInternalCustomer(_)), cc)  
      
    case OutboundGetCustomersByUserId(userId, cc) =>
      val result: Box[List[Customer]] = getCustomersByUserId(userId, None).map(r => r._1)
      sender ! InboundGetCustomersByUserId(result.getOrElse(Nil).map(Transformer.toInternalCustomer(_)), cc)
       
    case OutboundGetCounterparties(thisBankId, thisAccountId, viewId, cc) =>
      val result: Box[List[CounterpartyTrait]] = getCounterparties(BankId(thisBankId), AccountId(thisAccountId), ViewId(viewId), None).map(r => r._1)
      sender ! InboundGetCounterparties(result.getOrElse(Nil).map(Transformer.toInternalCounterparty(_)), cc)
        
    case OutboundGetTransactions(bankId, accountId, limit, fromDate, toDate, cc) =>
      val from = APIUtil.DateWithMsFormat.parse(fromDate)
      val to = APIUtil.DateWithMsFormat.parse(toDate)
      val result = getTransactions(BankId(bankId), AccountId(accountId), None, List(OBPLimit(limit), OBPFromDate(from), OBPToDate(to)): _*).map(r => r._1)
      sender ! InboundGetTransactions(result.getOrElse(Nil).map(Transformer.toInternalTransaction(_)), cc)
        
    case OutboundGetTransaction(bankId, accountId, transactionId, cc) =>
      val result = getTransaction(BankId(bankId), AccountId(accountId), TransactionId(transactionId),  None).map(r => r._1)
      sender ! InboundGetTransaction(result.map(Transformer.toInternalTransaction(_)), cc)

    case message => 
      logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)
      
  }

}



object Transformer {
  def bank(mb: MappedBank): Bank = 
    Bank(
      bankId=mb.bankId.value,
      shortName=mb.shortName,
      fullName=mb.fullName,
      logoUrl=mb.logoUrl,
      websiteUrl=mb.websiteUrl,
      bankRoutingScheme=mb.bankRoutingScheme,
      bankRoutingAddress=mb.bankRoutingAddress
    )
  
  def bankAccount(acc: BankAccount) =
    InboundAccountDec2018(
      bankId = acc.bankId.value,
      branchId = acc.branchId,
      accountId = acc.accountId.value,
      accountNumber = acc.number,
      accountType = acc.accountType,
      balanceAmount = acc.balance.toString(),
      balanceCurrency = acc.currency,
      owners = acc.customerOwners.map(_.customerId).toList,
      viewsToGenerate = Nil,
      bankRoutingScheme = acc.bankRoutingScheme,
      bankRoutingAddress = acc.bankRoutingAddress,
      branchRoutingScheme = "",
      branchRoutingAddress = "",
      accountRoutingScheme = acc.accountRoutingScheme,
      accountRoutingAddress = acc.accountRoutingAddress,
      accountRouting = Nil,
      accountRules = Nil
    )
  
  def coreAccount(a: CoreAccount) =
    InternalInboundCoreAccount(
      id = a.id,
      label = a.label,
      bankId = a.bankId,
      accountType = a.accountType,
      accountRoutings = a.accountRoutings
    )

  def toInternalCustomer(customer: Customer): InternalCustomer = {
    InternalCustomer(
      customerId = customer.customerId,
      bankId = customer.bankId,
      number = customer.number,
      legalName = customer.legalName,
      mobileNumber = customer.mobileNumber,
      email = customer.email,
      faceImage = CustomerFaceImage(customer.faceImage.date,customer.faceImage.url),
      dateOfBirth = customer.dateOfBirth,
      relationshipStatus = customer.relationshipStatus,
      dependents = customer.dependents,
      dobOfDependents = customer.dobOfDependents,
      highestEducationAttained = customer.highestEducationAttained,
      employmentStatus = customer.employmentStatus,
      creditRating = CreditRating(customer.creditRating.rating, customer.creditRating.source),
      creditLimit = CreditLimit(customer.creditLimit.amount,customer.creditLimit.currency),
      kycStatus = customer.kycStatus,
      lastOkDate = customer.lastOkDate,
    )
  }
  
  def toInternalCounterparty(c: CounterpartyTrait) = {
    InternalCounterparty(
      createdByUserId=c.createdByUserId,
      name=c.name,
      thisBankId=c.thisBankId,
      thisAccountId=c.thisAccountId,
      thisViewId=c.thisViewId,
      counterpartyId=c.counterpartyId,
      otherAccountRoutingScheme=c.otherAccountRoutingScheme,
      otherAccountRoutingAddress=c.otherAccountRoutingAddress,
      otherBankRoutingScheme=c.otherBankRoutingScheme,
      otherBankRoutingAddress=c.otherBankRoutingAddress,
      otherBranchRoutingScheme=c.otherBankRoutingScheme,
      otherBranchRoutingAddress=c.otherBranchRoutingAddress,
      isBeneficiary=c.isBeneficiary,
      description=c.description,
      otherAccountSecondaryRoutingScheme=c.otherAccountSecondaryRoutingScheme,
      otherAccountSecondaryRoutingAddress=c.otherAccountSecondaryRoutingAddress,
      bespoke=c.bespoke
    )
  }

  def toInternalTransaction(t: Transaction): InternalTransaction_vDec2018 = {
    InternalTransaction_vDec2018(
      uuid = t.uuid ,
      id  = t.id ,
      thisAccount = t.thisAccount ,
      otherAccount = t.otherAccount ,
      transactionType = t.transactionType ,
      amount = t.amount ,
      currency = t.currency ,
      description = t.description ,
      startDate = t.startDate ,
      finishDate = t.finishDate ,
      balance = t.balance
    )
  }
}

