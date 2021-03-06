package com.victormiranda.mani.scraper.processor.ptsb;

import com.victormiranda.mani.bean.AccountInfo;
import com.victormiranda.mani.bean.BaseAccountInfo;
import com.victormiranda.mani.bean.Transaction;
import com.victormiranda.mani.scraper.bean.NavigationSession;
import com.victormiranda.mani.scraper.processor.BaseProcessor;
import com.victormiranda.mani.scraper.processor.TransactionProcessor;
import com.victormiranda.mani.scraper.type.PTSBUrl;
import com.victormiranda.mani.type.TransactionFlow;
import com.victormiranda.mani.type.TransactionStatus;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class PTSBTransactionProcessor extends BaseProcessor implements TransactionProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PTSBTransactionProcessor.class.getName());

   private static final DateTimeFormatter dateFieldDateFormatter = DateTimeFormatter.ofPattern("dd MMM yy");

    @Override
    public List<Transaction> processTransactions(final AccountInfo accountInfo, final Optional<LocalDate> lastSync, final NavigationSession navigationSession) {
        URL url = PTSBUrl.getURL(lastSync).url;

        final Map<String, String> accountParam =  new HashMap<>(1);
        accountParam.put("{uid}", accountInfo.getUid());

        final Document document = parse(expandURL(url, accountParam), Connection.Method.GET, navigationSession);

        final List<Transaction> transactions = new ArrayList<>();

        final Elements rawTransactions = document.select("table.header-big tbody tr");

        for (Element e : rawTransactions) {
            final Transaction transactionProcessed = getTransaction(e, accountInfo, TransactionStatus.NORMAL);

            LOGGER.info("Processed transaction " + transactionProcessed);

            transactions.add(transactionProcessed);
        }

        transactions.addAll(processPendingTransactions(document, accountInfo, navigationSession));

        return transactions;
    }

    private List<Transaction> processPendingTransactions(final Document document, final BaseAccountInfo account, final NavigationSession navigationSession) {
        final List<Transaction> pendingTransactions = new ArrayList<>();

        final Elements existentPendingCandidates = document.select(".module-sub-nav a");
        for (Element candidate : existentPendingCandidates) {
            final String href = candidate.attr("href");

            if (href.contains("PendingTransactions?accountId=") && candidate.text().matches(".*[(][0-9]{1,3}[)]")) {
                final Map<String, String> accountUid = new HashMap<>();
                accountUid.put("{uid}", href.substring("accountId=".length() + href.indexOf("accountId=")));

                URL url = expandURL(expandURL(PTSBUrl.ACCOUNT_PENDINGS.url, accountUid), accountUid);
                final Document pendingsDoc = parse(url, Connection.Method.GET, navigationSession);
                final Elements rawTransactions = pendingsDoc.select("table tbody tr");

                pendingTransactions.addAll(
                        rawTransactions.stream()
                                .map(e -> getTransaction(e, account, TransactionStatus.PENDING))
                                .collect(Collectors.toList()));
            }
        }

        return pendingTransactions;
    }

    private Transaction getTransaction(final Element e, final BaseAccountInfo account, final TransactionStatus transactionStatus) {
        final String desc = e.select(".desc").text();
        final String valIn;
        final String valOut;
        final BigDecimal balance;

        if (transactionStatus == TransactionStatus.NORMAL) {
             valIn = e.select("[data-money=in]").text();
             valOut = e.select("[data-money=out]").text();
             balance = BaseProcessor.money(e.select(".currency").last().text());
        } else {
            valIn = "";
            valOut = e.select(".currency").last().text();
            balance = null;
        }

        final TransactionFlow transactionFlow;
        final BigDecimal transactionAm;

        if (valIn.length() == 0) {
            transactionFlow = TransactionFlow.OUT;
            transactionAm = BaseProcessor.money(valOut);
        } else {
            transactionFlow = TransactionFlow.IN;
            transactionAm = BaseProcessor.money(valIn);
        }

        final LocalDate transactionDate = processDate(e.select(".date").text());

        return new Transaction.Builder()
                .withUid(e.attr("data-uid"))
                .withDescription(desc)
                .withAccount(account)
                .withFlow(transactionFlow)
                .withAmount(transactionAm)
                .withBalance(balance)
                .withDateSettled(transactionDate)
                .withStatus(transactionStatus)
                .build();
    }

    private LocalDate processDate(final String dateFromDateField) {
        return LocalDate.parse(dateFromDateField, dateFieldDateFormatter);
    }
}
