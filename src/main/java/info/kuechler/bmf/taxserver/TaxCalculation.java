package info.kuechler.bmf.taxserver;

import java.math.BigDecimal;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;

import org.reactivestreams.Publisher;
import org.springframework.stereotype.Component;

import info.kuechler.bmf.taxapi.Ausgabe;
import info.kuechler.bmf.taxapi.Eingabe;
import info.kuechler.bmf.taxapi.Lohnsteuer;
import info.kuechler.bmf.taxapi.ObjectFactory;
import info.kuechler.bmf.taxapi.Type;
import info.kuechler.bmf.taxcalculator.rw.ReadWriteException;
import info.kuechler.bmf.taxcalculator.rw.Reader;
import info.kuechler.bmf.taxcalculator.rw.TaxCalculatorFactory;
import info.kuechler.bmf.taxcalculator.rw.Writer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class TaxCalculation {

    private final ObjectFactory objectFactory;

    private final TaxCalculatorFactory taxCalculatorFactory;

    public TaxCalculation() {
        super();
        this.objectFactory = new ObjectFactory();
        this.taxCalculatorFactory = new TaxCalculatorFactory();
    }

    // Hint: yes, this is a playground
    public Mono<Lohnsteuer> calculate(final Flux<Entry<String, String>> inputParameters, final int yearParameter,
            final int monthParameter) {

        final Mono<String> classKey = Mono
                .fromSupplier(() -> getTaxCalculatorFactory().getYearKey(monthParameter, yearParameter));
        final Mono<Lohnsteuer> tax = Mono.fromSupplier(() -> getObjectFactory().createLohnsteuer());
        final Mono<Integer> year = Mono.just(yearParameter);

        // get inputs objects...
        // ...and output names...

        final Flux<Entry<String, ?>> inputValues = Flux.combineLatest(classKey.map(this::getInputTypes),
                inputParameters, this::createInputValue);

        final Flux<String> outputNames = classKey.map(this::getOutputTypes).flatMapIterable(Map::entrySet)
                .filter(ot -> ot.getValue() == BigDecimal.class).map(Entry::getKey);

        // ...create writer, set inputs and calculate...

        final Mono<Reader> reader = combineToMono(classKey.map(this::createWriter), inputValues, this::setInputs)
                .map(this::createReader);

        // ...build result lists...

        final Mono<List<Ausgabe>> outputs = Flux.combineLatest(reader, outputNames, this::createOutput).collectList();
        final Mono<List<Eingabe>> inputs = inputParameters.map(this::createInput).collectList();

        // ...fill result object...

        final Mono<Lohnsteuer> tax1 = combineToMono(tax, year, (t, y) -> t.setJahr(Integer.toString(y)));
        final Mono<Lohnsteuer> tax2 = combineToMono(tax1, inputs, (t, in) -> t.setEingaben(in));
        return combineToMono(tax2, outputs, (t, out) -> t.setAusgaben(out));
    }

    protected Object convertToType(final String value, final Class<?> type) {
        if (value == null || "".equals(value)) {
            return null;
        }
        if (type == BigDecimal.class) {
            return new BigDecimal(value);
        }
        if (type == int.class) {
            return Integer.parseInt(value);
        }
        if (type == double.class) {
            return Double.parseDouble(value);
        }
        return value;
    }

    protected Eingabe createInput(Entry<String, String> inputParameter) {
        final Eingabe eingabe = getObjectFactory().createEingabe();
        eingabe.setName(inputParameter.getKey());
        try {
            eingabe.setValue(new BigDecimal(inputParameter.getValue()));
            eingabe.setStatus("ok");
        } catch (NumberFormatException e) {
            eingabe.setStatus("nok");
        }
        return eingabe;
    }

    protected Ausgabe createOutput(final Reader reader, final String outputName) {
        try {
            final Ausgabe output = getObjectFactory().createAusgabe();
            output.setName(outputName);
            output.setType(Type.STANDARD);
            output.setValue(reader.getBigDecimal(outputName));
            return output;
        } catch (ReadWriteException e) {
            throw new IllegalArgumentException("Error during read");
        }
    }

    protected Map<String, Class<?>> getInputTypes(final String classKey) {
        try {
            return getTaxCalculatorFactory().getInputsWithType(classKey);
        } catch (ReadWriteException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected Map<String, Class<?>> getOutputTypes(final String classKey) {
        try {
            return getTaxCalculatorFactory().getOutputsWithType(classKey);
        } catch (ReadWriteException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected Writer createWriter(final String classKey) {
        try {
            return getTaxCalculatorFactory().create(classKey).setAllToZero();
        } catch (ReadWriteException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected Reader createReader(final Writer writer) {
        try {
            return writer.calculate();
        } catch (ReadWriteException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected Entry<String, ?> createInputValue(final Map<String, Class<?>> inTypes,
            final Entry<String, String> inputParameter) {
        return new SimpleImmutableEntry<>(inputParameter.getKey(),
                convertToType(inputParameter.getValue(), inTypes.get(inputParameter.getKey())));
    }

    protected void setInputs(final Writer writer, final Entry<String, ?> inputValue) {
        try {
            writer.set(inputValue.getKey(), inputValue.getValue());
        } catch (ReadWriteException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static <T1, T2> Mono<T1> combineToMono(final Mono<? extends T1> source1,
            final Publisher<? extends T2> source2, final BiConsumer<? super T1, ? super T2> combinator) {
        return Flux.combineLatest(source1, source2, (T1 t1, T2 t2) -> {
            combinator.accept(t1, t2);
            return t1;
        }).last();
    }

    public ObjectFactory getObjectFactory() {
        return objectFactory;
    }

    public TaxCalculatorFactory getTaxCalculatorFactory() {
        return taxCalculatorFactory;
    }
}
