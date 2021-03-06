package com.victormiranda.mani.scraper.processor.ptsb;

import com.victormiranda.mani.bean.AccountInfo;
import com.victormiranda.mani.scraper.bean.LoggedNavigationSession;
import com.victormiranda.mani.scraper.processor.AccountProcessor;
import com.victormiranda.mani.scraper.processor.BaseProcessor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Component
public class PTSBAccountProcessor extends BaseProcessor implements AccountProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PTSBAccountProcessor.class.getName());

    @Override
    public Set<AccountInfo> processAccounts(final LoggedNavigationSession navigationSession) {
        LOGGER.info("Processing accounts");

        final Document document = navigationSession.getDashboard();

        final Elements accountCandidates = document.select(".module-account");
        final Set<AccountInfo> accounts = new HashSet<>(accountCandidates.size());

        for (Element e: accountCandidates) {
            final boolean isAccount = !e.select(".heading-general").isEmpty();
            if (isAccount) {
                final String uid = e.select(".heading-general a").attr("href").split("=")[1];
                final String name = document.select("option[value=" + uid + "]").first().text();
                final String accountNumber = name.substring(name.lastIndexOf(" ") + 1);
                final BigDecimal availableBalance = BaseProcessor.money(e.select(".funds .fund-1").text());
                //.fund-2 also stores payment due in credit card accounts
                final BigDecimal currentBalance = BaseProcessor.money(e.select(".funds .fund-2").first().text());

                accounts.add(
                    new AccountInfo.Builder()
                        .withName(name)
                        .withAccountNumber(accountNumber)
                        .withAlias(name)
                        .withUid(uid)
                        .withAvailableBalance(availableBalance)
                        .withCurrentBalance(currentBalance)
                        .withLastSynced(LocalDate.now())
                        .build());
            }
        }

        return accounts;
    }

}
