package me.rerere.rikkahub.di

import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.memory.engine.EntityResolver
import me.rerere.rikkahub.data.memory.engine.KnowledgeExtractor
import me.rerere.rikkahub.data.memory.engine.MemoryConsolidationEngine
import me.rerere.rikkahub.data.memory.migration.LegacyMigrator
import me.rerere.rikkahub.data.memory.repository.KnowledgeGraphRepository
import me.rerere.rikkahub.data.memory.service.MemoryExtractionService
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import org.koin.dsl.module

val repositoryModule = module {
    single {
        ConversationRepository(get(), get(), get())
    }

    single {
        EmbeddingService(get(), get())
    }

    single {
        MemoryRepository(get(), get(), get(), get())
    }

    single {
        GenMediaRepository(get())
    }
    
    // Knowledge Graph dependencies
    single {
        KnowledgeGraphRepository(
            dao = get(),
            embeddingService = get()
        )
    }
    
    single {
        KnowledgeExtractor(
            providerManager = get(),
            settingsStore = get()
        )
    }
    
    single {
        EntityResolver(
            dao = get(),
            embeddingService = get()
        )
    }
    
    single {
        MemoryConsolidationEngine(
            dao = get(),
            embeddingService = get(),
            providerManager = get(),
            settingsStore = get()
        )
    }
    
    single {
        LegacyMigrator(
            oldMemoryDAO = get(),
            oldEpisodeDAO = get(),
            newGraphDAO = get(),
            extractor = get(),
            embeddingService = get()
        )
    }
    
    // Real-time memory extraction service
    single {
        MemoryExtractionService(
            knowledgeExtractor = get(),
            knowledgeGraphRepository = get()
        )
    }
}


