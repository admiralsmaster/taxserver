package info.kuechler.bmf.taxserver;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import info.kuechler.bmf.taxapi.Lohnsteuer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/interface")
public class TaxController {

    @Autowired
    private TaxCalculation taxCalculation;

    @GetMapping("/{year}")
    public Mono<Lohnsteuer> calc(final WebRequest webRequest, @PathVariable final int year) {
        return calc(webRequest, year, 0);
    }

    @GetMapping("/{year}/{month}")
    public Mono<Lohnsteuer> calc(final WebRequest webRequest, @PathVariable final int year,
            @PathVariable final int month) {
        final Flux<Entry<String, String>> inputParameter = Mono.fromSupplier(() -> webRequest.getParameterMap())
                .flatMapIterable(Map::entrySet)
                .map(entry -> new SimpleImmutableEntry<>(entry.getKey(), entry.getValue()[0]));
        return taxCalculation.calculate(inputParameter, year, month);
    }
}
