SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[terminliste_domainevent2](
    [globalIndex] [int] IDENTITY,
    [aggregateIdentifier] [nvarchar](64) NOT NULL,
    [sequenceNumber] [bigint] NOT NULL,
    [type] [nvarchar](255) NOT NULL,
    [eventIdentifier] [nvarchar](64) NOT NULL,
    [metaData] [varbinary](max) NULL,
    [payload] [varbinary](max) NOT NULL,
    [payloadRevision] [nvarchar](255) NULL,
    [payloadType] [nvarchar](255) NOT NULL,
    [timeStamp] [nvarchar](32) NOT NULL,
    PRIMARY KEY CLUSTERED
(
    [aggregateIdentifier] ASC,
    [sequenceNumber] ASC,
[type] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY],
    CONSTRAINT [UK_terminlisteEventstore] UNIQUE NONCLUSTERED
(
[eventIdentifier] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
    ) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
    GO
    SET ANSI_PADDING ON
    GO
CREATE NONCLUSTERED INDEX [terminliste_domainevent_axon] ON [dbo].[terminliste_domainevent2]
(
	[type] ASC,
	[sequenceNumber] ASC,
	[aggregateIdentifier] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO

CREATE NONCLUSTERED INDEX [terminliste_domainevent_timeStamp] ON [dbo].[terminliste_domainevent2]
(
	[timeStamp] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO

insert into terminliste_domainevent2 (aggregateIdentifier, sequenceNumber, [type], eventIdentifier, metaData, payload, payloadRevision, payloadType, [timeStamp]) select aggregateIdentifier, sequenceNumber, [type], eventIdentifier, metaData, payload, payloadRevision, payloadType, [timeStamp] from terminliste_domainevent order by [timeStamp], aggregateIdentifier, sequenceNumber


drop table terminliste_domainevent;
GO

exec sp_rename 'terminliste_domainevent2', 'terminliste_domainevent';
