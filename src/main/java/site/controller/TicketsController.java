package site.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import org.springframework.web.bind.annotation.ResponseBody;
import site.controller.epay.EpayRaw;
import site.controller.epay.EpayResponse;
import site.controller.epay.EpayUtil;
import site.facade.RegistrantFacade;
import site.facade.UserFacade;
import site.model.JprimeException;
import site.model.Registrant;

import javax.servlet.http.HttpServletRequest;
import javax.transaction.Transactional;
import javax.validation.Valid;
import java.util.Map;

/**
 * All the epay stuff is in the {@link EpayUtil} class.
 *
 * @author Mihail Stoynov
 */
@Controller
public class TicketsController {

    static final String TICKETS_JSP = "/tickets-intro.jsp";

    @Autowired
    @Qualifier(UserFacade.NAME)
    private UserFacade userFacade;

    @Autowired
    @Qualifier(RegistrantFacade.NAME)
    private RegistrantFacade registrantFacade;

    @RequestMapping(value = "/tickets", method = RequestMethod.GET)
    public String goToRegisterPage(Model model) {
        model.addAttribute("tags", userFacade.findAllTags());
        model.addAttribute("registrant", new Registrant());
        return "/tickets-register.jsp";
    }

    @Transactional
    @RequestMapping(value = "/tickets", method = RequestMethod.POST)
    public String register(Model model, @Valid final Registrant registrant, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "/tickets-register.jsp";
        }

        Registrant savedRegistrant = registrantFacade.save(registrant);

        model.addAttribute("tags", userFacade.findAllTags());
//        model.addAttribute("registrant", registrant);
        prepareEpay(model, savedRegistrant);
        return "/tickets-buy.jsp";
    }

    //    @RequestMapping(value = "/tickets/buy", method = RequestMethod.GET)
    public String prepareEpay(Model model, Registrant registrant) {
        EpayRaw epayRaw = EpayUtil.encrypt(registrant.getVisitors().size(), registrant.getInvoiceNumber());
        model.addAttribute("ENCODED", epayRaw.getEncoded());
        model.addAttribute("CHECKSUM", epayRaw.getChecksum());
//        model.addAttribute("facNo", registrant.getFacNo());
        model.addAttribute("epayUrl", EpayUtil.EPAY_URL);

        model.addAttribute("tags", userFacade.findAllTags());
        return "/tickets/buy";
    }


    /**
     * Receiving data from epay.bg
     */
    @RequestMapping(value = "/tickets/from.epay", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.HEAD})
    @ResponseBody//we return the string literal
    public String receiveFromEpay(HttpServletRequest request) {
        System.out.println("EPAY");
        Map<String, String[]> parameters = request.getParameterMap();
        try {
            String encoded = parameters.get("encoded")[0];
            String checksum = parameters.get("checksum")[0];
            EpayRaw epayRaw = new EpayRaw(checksum, encoded);
            EpayResponse epayResponse = EpayUtil.decrypt(epayRaw);

            Registrant registrant = registrantFacade.findByInvoiceNumber(epayResponse.getInvoiceNumber());
            registrant.setEpayResponse(epayResponse);
            registrant = registrantFacade.save(registrant);
            createPDFAndSendToEmail(registrant);
            return epayResponse.getEpayAnswer();
        } catch (Throwable t) {
            throw new JprimeException("epay response parsing failed", t);
        }
    }

    @RequestMapping(value = "/tickets/result/{r}", method = RequestMethod.GET)
    public String result(@PathVariable("r") final String r, Model model) {
        model.addAttribute("result", r.equals("ok"));
        return "/tickets-result.jsp";
    }

    //TODO
    private void createPDFAndSendToEmail(Registrant registrant) {
        //TODO create PDF for registrant
        registrant.getName();//ime na firmata
        registrant.getMol();//mol
        registrant.getAddress();//address
        registrant.getVatNumber();//BULSTAT

        //SEND TO THIS EMAIL
        registrant.getEmail();
    }
}