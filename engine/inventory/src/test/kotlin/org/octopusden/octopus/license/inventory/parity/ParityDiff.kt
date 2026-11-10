package org.octopusden.octopus.license.inventory.parity

/** Every way the service's view of one component version differs from the prototype's, one line each. */
object ParityDiff {
    fun diff(
        prototype: ParityView,
        service: ParityView,
    ): List<String> = builds(prototype, service) + files(prototype, service) + packages(prototype, service) + findings(prototype, service)

    private fun builds(
        prototype: ParityView,
        service: ParityView,
    ) = if (prototype.builds == service.builds) emptyList() else listOf("builds: prototype ${prototype.builds}, service ${service.builds}")

    // Files are matched by entry and occurrence, so a reordering shows as one line, not as every file differing.
    private fun files(
        prototype: ParityView,
        service: ParityView,
    ): List<String> {
        val ours = service.files.withOccurrence()
        val theirs = prototype.files.withOccurrence()
        val order =
            if (prototype.files.map { it.entry } != service.files.map { it.entry } && theirs.keys == ours.keys) {
                listOf("file order: prototype ${prototype.files.map { it.entry }}, service ${service.files.map { it.entry }}")
            } else {
                emptyList()
            }
        return order +
            (theirs.keys - ours.keys).map { "file ${it.first}: only in the prototype" } +
            (ours.keys - theirs.keys).map { "file ${it.first}: only in the service" } +
            theirs.keys.intersect(ours.keys).flatMap { key -> fields(key.first, theirs.getValue(key), ours.getValue(key)) }
    }

    private fun fields(
        entry: String,
        prototype: ParityView.FileView,
        service: ParityView.FileView,
    ) = listOf(
        "kind" to (prototype.kind to service.kind),
        "declared" to (prototype.declared to service.declared),
        "selected by" to (prototype.selectedBy to service.selectedBy),
        "path" to (prototype.path to service.path),
        "build" to (prototype.build to service.build),
        "build found by" to (prototype.buildFoundBy to service.buildFoundBy),
        "status" to (prototype.status to service.status),
        "no build" to (prototype.noBuild to service.noBuild),
    ).filter { (_, values) -> values.first != values.second }
        .map { (field, values) -> "file $entry: $field: prototype ${values.first}, service ${values.second}" }

    private fun packages(
        prototype: ParityView,
        service: ParityView,
    ) = (prototype.packages.keys - service.packages.keys).sorted().map { "package $it: only in the prototype" } +
        (service.packages.keys - prototype.packages.keys).sorted().map { "package $it: only in the service" } +
        prototype.packages.keys.intersect(service.packages.keys).sorted().flatMap { id ->
            val theirs = prototype.packages.getValue(id)
            val ours = service.packages.getValue(id)
            listOf(
                "licenses" to (theirs.licenses to ours.licenses),
                "sources" to (theirs.sources to ours.sources),
                "files" to (theirs.files to ours.files),
            ).filter { (_, values) -> values.first != values.second }
                .map { (field, values) -> "package $id: $field: prototype ${values.first.sorted()}, service ${values.second.sorted()}" }
        }

    private fun findings(
        prototype: ParityView,
        service: ParityView,
    ) = (prototype.findings - service.findings).map { "finding only in the prototype: ${it.code} ${it.subject} ${it.detail}" } +
        (service.findings - prototype.findings).map { "finding only in the service: ${it.code} ${it.subject} ${it.detail}" }

    private fun List<ParityView.FileView>.withOccurrence(): Map<Pair<String, Int>, ParityView.FileView> {
        val seen = mutableMapOf<String, Int>()
        return associateBy { file -> file.entry to seen.merge(file.entry, 1, Int::plus)!! }
    }
}
