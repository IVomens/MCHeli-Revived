package com.ivomens.mcheli.content.registry;

import com.ivomens.mcheli.core.content.schema.ContentDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * All loaded content of one type, keyed by file name without extension (for example {@code "ah-64"}), with the
 * run-time value the owning feature builds from each file. The feature creates the registry with its
 * {@link Binder} and hands it to {@link ContentRegistries#register}; the content pipeline fills it.
 * <p>
 * Entries can be read again from disk in game with {@link #reload} and {@link #reloadAll}.
 *
 * @param <D> the definition the files read into
 * @param <T> the run-time value of one file
 */
public final class ContentRegistry<D extends ContentDefinition, T> {

    /** Builds the run-time value of a file that was just read. */
    @FunctionalInterface
    public interface Binder<D extends ContentDefinition, T> {

        /**
         * Called once per file, right after it is read. Throwing rejects the file: the loader logs the problem
         * with the file name and skips it.
         */
        T bind(ContentEntry<D> entry) throws Exception;
    }

    private final ContentType type;
    private final Class<D> definitionType;
    private final Binder<D, T> binder;
    private final Consumer<T> onReloaded;
    private final List<Runnable> changeListeners = new ArrayList<>();
    /** Replaced as a whole, so readers never see a half-built table. */
    private volatile Map<String, LoadedContent<D, T>> byName = Map.of();

    /**
     * @param onReloaded called with the new value of a file read again in game, before it replaces the old one
     */
    public ContentRegistry(ContentType type, Class<D> definitionType, Binder<D, T> binder, Consumer<T> onReloaded) {
        this.type = type;
        this.definitionType = definitionType;
        this.binder = binder;
        this.onReloaded = onReloaded;
    }

    public ContentRegistry(ContentType type, Class<D> definitionType, Binder<D, T> binder) {
        this(type, definitionType, binder, value -> {
        });
    }

    public ContentType type() {
        return this.type;
    }

    /** Runs {@code listener} whenever the registry was filled or entries were read again. */
    public void addChangeListener(Runnable listener) {
        this.changeListeners.add(listener);
    }

    /** The run-time value of the named file. */
    public Optional<T> get(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(this.byName.get(name)).map(LoadedContent::value);
    }

    /** The named file as read. */
    public Optional<ContentEntry<D>> entry(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(this.byName.get(name)).map(LoadedContent::entry);
    }

    /** The first value that matches, in table order. */
    public Optional<T> find(Predicate<? super T> filter) {
        for (LoadedContent<D, T> loaded : this.byName.values()) {
            if (filter.test(loaded.value())) {
                return Optional.of(loaded.value());
            }
        }
        return Optional.empty();
    }

    public boolean contains(String name) {
        return this.byName.containsKey(name);
    }

    public int size() {
        return this.byName.size();
    }

    /** Every value, in table order. */
    public List<T> values() {
        return this.byName.values().stream().map(LoadedContent::value).toList();
    }

    /** Every file with its value, in table order. */
    public Collection<LoadedContent<D, T>> loaded() {
        return this.byName.values();
    }

    /**
     * Reads one entry again from its file; a file that can no longer be read keeps its old entry. Returns false
     * when the name is unknown.
     */
    public boolean reload(String name) {
        LoadedContent<D, T> old = this.byName.get(name);
        if (old == null) {
            return false;
        }
        LoadedContent<D, T> reloaded = ContentRegistries.reread(this, old);
        Map<String, LoadedContent<D, T>> table = new HashMap<>(this.byName);
        table.replace(name, inheritAuthor(old, reloaded));
        this.byName = table;
        changed();
        return true;
    }

    /** Reads every entry again from the loaded addon packs; entries that cannot be read keep their old value. */
    public void reloadAll() {
        Map<String, LoadedContent<D, T>> table = new HashMap<>(this.byName);
        for (LoadedContent<D, T> reloaded : ContentRegistries.rereadAll(this)) {
            table.replace(reloaded.name(), inheritAuthor(table.get(reloaded.name()), reloaded));
        }
        this.byName = table;
        changed();
    }

    /** The content pipeline: builds the value of a file that was just read; see {@link Binder#bind}. */
    public LoadedContent<D, T> bind(ContentEntry<?> entry) throws Exception {
        ContentEntry<D> typed = cast(entry);
        return new LoadedContent<>(typed, this.binder.bind(typed));
    }

    /** The content pipeline: a file was read again in game and is about to replace its old entry. */
    public void reloaded(LoadedContent<D, T> loaded) {
        this.onReloaded.accept(loaded.value());
    }

    /** The content pipeline: a file this registry's {@link #bind} built, typed again. */
    @SuppressWarnings("unchecked")
    public LoadedContent<D, T> cast(LoadedContent<?, ?> loaded) {
        cast(loaded.entry());
        return (LoadedContent<D, T>) loaded;
    }

    @SuppressWarnings("unchecked")
    private ContentEntry<D> cast(ContentEntry<?> entry) {
        if (entry.type() != this.type || !this.definitionType.isInstance(entry.definition())) {
            throw new IllegalArgumentException("not a " + this.type.dirName + " entry: " + entry.filePath());
        }
        return (ContentEntry<D>) entry;
    }

    /**
     * The content pipeline: replaces the whole table with {@code files}, in load order, where a later file of
     * the same name wins. Listeners are told by {@link #changed}.
     */
    public void fill(List<LoadedContent<D, T>> files) {
        Map<String, LoadedContent<D, T>> table = new HashMap<>();
        for (LoadedContent<D, T> file : files) {
            table.put(file.name(), inheritAuthor(table.get(file.name()), file));
        }
        this.byName = new HashMap<>(table);
    }

    /** The content pipeline: tells the listeners that the table was filled or entries were read again. */
    public void changed() {
        this.changeListeners.forEach(Runnable::run);
    }

    /**
     * When an addon overrides content of another pack without naming an author, the original author is kept so
     * the credit is not lost.
     */
    private LoadedContent<D, T> inheritAuthor(LoadedContent<D, T> previous, LoadedContent<D, T> content) {
        if (previous == null) {
            return content;
        }
        String previousDomain = previous.entry().location().getAddonDomain();
        if (previousDomain.equals(content.entry().location().getAddonDomain())) {
            return content;
        }
        String author = content.entry().author();
        String previousAuthor = previous.entry().author();
        return author.isBlank() && !previousAuthor.isBlank() ? content.withAuthor(previousAuthor) : content;
    }
}
